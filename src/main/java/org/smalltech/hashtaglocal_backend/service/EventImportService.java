package org.smalltech.hashtaglocal_backend.service;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.smalltech.hashtaglocal_backend.dto.ScrapeEventDTO;
import org.smalltech.hashtaglocal_backend.entity.EventApprovalEntity;
import org.smalltech.hashtaglocal_backend.entity.EventEntity;
import org.smalltech.hashtaglocal_backend.entity.MediaEntity;
import org.smalltech.hashtaglocal_backend.model.EventApprovalStatus;
import org.smalltech.hashtaglocal_backend.model.EventPortalModel;
import org.smalltech.hashtaglocal_backend.model.EventTypeModel;
import org.smalltech.hashtaglocal_backend.repository.EventApprovalRepository;
import org.smalltech.hashtaglocal_backend.repository.EventRepository;
import org.smalltech.hashtaglocal_backend.service.scraper0.Scraper0Models.EventData;
import org.smalltech.hashtaglocal_backend.service.scraper0.Scraper0Models.Item;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Imports scraped events: the items of a scraper0 event source ({@link #importItems}) and the
 * legacy scrape payload of {@code POST /api/v1/events/ingest} ({@link #importFromScrapeResponse}).
 *
 * <p>Key behaviours:
 *
 * <ul>
 *   <li>Events with a blank {@code name}, no {@code startTime} or no image are skipped; the image
 *       is copied to GCS, and every new event gets a PENDING approval row.
 *   <li>Deduplication: a scraper0 item whose id is already in {@code external_id} is skipped, and
 *       so is an event with the same {@code name} + {@code startTime} as an existing row.
 *   <li>Geocoding (address → lat/lng) is NOT performed here. The {@code location_id} field is left
 *       null; only the raw {@code address} string is saved. Run {@code POST /admin/events/geocode}
 *       afterwards.
 * </ul>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class EventImportService {

  // Mirrors the one-time DB cleanup: UPDATE events SET link = regexp_replace(link,
  // '&utm_[^&]*=[^&]*', '', 'g') WHERE link LIKE '%utm_%'. Applied to every new link
  // before saving so freshly imported rows stay consistent with the cleaned-up ones.
  // '&utm_...' only ever matched non-first params in that cleanup; freshly scraped links can
  // have utm as the FIRST query param (e.g. "?utm_source=fb&id=1"), so the two patterns below
  // handle promoting the following '&' to '?' (or dropping the '?' entirely if utm was the only
  // param) before the shared '&utm_...' pattern strips the rest.
  private static final Pattern LEADING_UTM_PARAM_PATTERN = Pattern.compile("\\?utm_[^&]*=[^&]*&");
  private static final Pattern SOLE_LEADING_UTM_PARAM_PATTERN =
      Pattern.compile("\\?utm_[^&]*=[^&]*$");
  private static final Pattern UTM_PARAM_PATTERN = Pattern.compile("&utm_[^&]*=[^&]*");

  private final EventService eventService;
  private final EventRepository eventRepository;
  private final EventImageService eventImageService;
  private final EventApprovalRepository eventApprovalRepository;

  /** One event to import, from either input shape. */
  private record Candidate(
      String name,
      String organisation,
      EventPortalModel portal,
      EventTypeModel type,
      LocalDateTime startTime,
      LocalDateTime endTime,
      String address,
      String link,
      String image,
      String externalId,
      String sourceSiteId) {}

  /**
   * Processes a list of events in the legacy scrape payload ({@code POST /api/v1/events/ingest}),
   * deduplicates against the database, and bulk-saves all new events in a single transaction.
   *
   * @param scrapeEvents raw event DTOs in the legacy scrape response shape
   * @return the number of events actually saved (duplicates excluded)
   */
  @Transactional
  public int importFromScrapeResponse(List<ScrapeEventDTO> scrapeEvents) {
    if (scrapeEvents == null || scrapeEvents.isEmpty()) {
      return 0;
    }
    List<Candidate> candidates = new ArrayList<>();
    for (ScrapeEventDTO dto : scrapeEvents) {
      if (!hasRequiredFields(dto.getName(), dto.getStartTime(), dto.getImage())) {
        continue;
      }
      // fromString() returns null for portals it doesn't recognise. portal is a NOT NULL
      // column, and the batch is bulk-inserted in one transaction, so letting a null portal
      // through here would roll back — and silently drop — the entire import batch.
      EventPortalModel portal = EventPortalModel.fromString(dto.getPortal());
      if (portal == null) {
        log.warn("Skipping event '{}' — unrecognized portal '{}'", dto.getName(), dto.getPortal());
        continue;
      }
      candidates.add(
          new Candidate(
              dto.getName(),
              dto.getOrganisation(),
              portal,
              parseEventType(dto.getType()),
              dto.getStartTime(),
              dto.getEndTime(),
              dto.getAddress(),
              dto.getLink(),
              dto.getImage(),
              null,
              null));
    }
    return saveNew(candidates, scrapeEvents.size());
  }

  /**
   * Imports the items a scraper0 read returned for one event source. Insert only: an item whose
   * {@code id} is already in {@code events.external_id} is skipped (updates are left for later), as
   * is an older row with the same name and start time.
   *
   * <p>The portal is the enum value of the three original sites, otherwise {@link
   * EventPortalModel#WEB}; {@code source_site_id} keeps the site. The type is the item's {@code
   * category}, {@code OTHER} when it is not an {@link EventTypeModel}.
   *
   * @return the number of events saved
   */
  @Transactional
  public int importItems(String siteId, List<Item> items) {
    if (items == null || items.isEmpty()) {
      return 0;
    }
    EventPortalModel portal = EventPortalModel.fromSiteId(siteId);
    Set<String> seenIds = new HashSet<>();
    List<Candidate> candidates = new ArrayList<>();
    for (Item item : items) {
      EventData d = item.data();
      if (d == null || item.id() == null || item.id().isBlank()) {
        log.debug("Skipping scraper0 item without id or data from {}", siteId);
        continue;
      }
      if (!seenIds.add(item.id())) {
        continue;
      }
      LocalDateTime start = parseTime(d.startTime());
      if (!hasRequiredFields(d.name(), start, d.image())) {
        continue;
      }
      String link = firstNonBlank(d.link(), d.sourceUrl());
      if (link == null) {
        log.debug("Skipping event '{}' — no link", d.name());
        continue;
      }
      String address = d.address();
      if (address == null || address.isBlank()) {
        if (!Boolean.TRUE.equals(d.isOnline())) {
          log.debug("Skipping event '{}' — no address", d.name());
          continue;
        }
        address = "Online";
      }
      if (eventRepository.existsByExternalId(item.id())) {
        log.debug("Skipping already imported item {} ('{}')", item.id(), d.name());
        continue;
      }
      candidates.add(
          new Candidate(
              d.name(),
              d.organisation(),
              portal,
              parseEventType(d.category()),
              start,
              parseTime(d.endTime()),
              address,
              link,
              d.image(),
              item.id(),
              siteId));
    }
    return saveNew(candidates, items.size());
  }

  private boolean hasRequiredFields(String name, LocalDateTime startTime, String image) {
    if (name == null || name.isBlank()) {
      log.debug("Skipping event with blank name");
      return false;
    }
    if (startTime == null) {
      log.debug("Skipping event '{}' — startTime is null", name);
      return false;
    }
    if (image == null || image.isBlank()) {
      log.debug("Skipping event '{}' — image is null or blank", name);
      return false;
    }
    return true;
  }

  /**
   * Shared tail of both imports: the legacy name + start time dedupe, the image copy to GCS, one
   * bulk save, and a PENDING approval row per saved event.
   */
  private int saveNew(List<Candidate> candidates, int received) {
    List<EventEntity> toSave = new ArrayList<>();

    for (Candidate c : candidates) {
      try {
        if (eventRepository.existsByNameAndStartTime(c.name(), c.startTime())) {
          log.debug("Skipping duplicate event '{}' at {}", c.name(), c.startTime());
          continue;
        }

        // Download the CDN image, upload to GCS, and persist a MediaEntity row.
        // Skip the event if the image can't be stored — an event without a valid image is unusable.
        MediaEntity media = eventImageService.downloadAndStore(c.image());
        if (media == null) {
          log.debug("Skipping event '{}' — image download/upload failed", c.name());
          continue;
        }

        toSave.add(toEntity(c, media));
      } catch (Exception e) {
        log.warn("Skipping event '{}' due to error: {}", c.name(), e.getMessage());
      }
    }

    List<EventEntity> saved = eventService.saveAll(toSave);

    // Create a PENDING approval row for every newly imported event so it lands in the
    // admin review queue before appearing on the public site.
    List<EventApprovalEntity> approvals =
        saved.stream()
            .map(
                e ->
                    EventApprovalEntity.builder()
                        .eventId(e.getId())
                        .status(EventApprovalStatus.PENDING)
                        .build())
            .toList();
    eventApprovalRepository.saveAll(approvals);

    log.info(
        "Imported {} new events ({} received, {} skipped)",
        toSave.size(),
        received,
        received - toSave.size());
    return toSave.size();
  }

  private EventEntity toEntity(Candidate c, MediaEntity media) {
    return EventEntity.builder()
        .name(c.name())
        .organisation(c.organisation())
        .portal(c.portal())
        .type(c.type())
        .startTime(c.startTime())
        .endTime(c.endTime())
        .address(c.address())
        .link(stripUtmParams(c.link()))
        .media(media)
        .externalId(c.externalId())
        .sourceSiteId(c.sourceSiteId())
        .build();
  }

  /**
   * scraper0 times are ISO-8601 UTC ({@code 2026-03-14T04:30:00.000Z}). They are stored as UTC
   * wall-clock {@link LocalDateTime}, as the legacy import did (Jackson reads a {@code Z} time as
   * UTC), so the name + start time dedupe matches older rows.
   */
  static LocalDateTime parseTime(String raw) {
    if (raw == null || raw.isBlank()) {
      return null;
    }
    String s = raw.trim();
    try {
      return OffsetDateTime.parse(s).withOffsetSameInstant(ZoneOffset.UTC).toLocalDateTime();
    } catch (DateTimeParseException ignored) {
      // No offset: take it as UTC already.
    }
    try {
      return LocalDateTime.parse(s);
    } catch (DateTimeParseException e) {
      log.debug("Unparseable time '{}'", raw);
      return null;
    }
  }

  private static String firstNonBlank(String a, String b) {
    if (a != null && !a.isBlank()) {
      return a;
    }
    return b != null && !b.isBlank() ? b : null;
  }

  private String stripUtmParams(String link) {
    if (link == null || link.isBlank()) {
      return link;
    }
    String result = LEADING_UTM_PARAM_PATTERN.matcher(link).replaceFirst("?");
    result = SOLE_LEADING_UTM_PARAM_PATTERN.matcher(result).replaceFirst("");
    return UTM_PARAM_PATTERN.matcher(result).replaceAll("");
  }

  /**
   * Converts a type string from the scrape response into an {@link EventTypeModel} enum value.
   *
   * <p>The input is expected to match the enum name exactly (e.g. {@code "BEACH_CLEANUP"}, {@code
   * "TREKANDPLOG"}). Unrecognised strings fall back to {@link EventTypeModel#OTHER}.
   */
  private EventTypeModel parseEventType(String raw) {
    if (raw == null || raw.isBlank()) {
      return EventTypeModel.OTHER;
    }
    try {
      return EventTypeModel.valueOf(raw.trim().toUpperCase(Locale.ENGLISH));
    } catch (IllegalArgumentException e) {
      log.warn("Unknown event type '{}', defaulting to OTHER", raw);
      return EventTypeModel.OTHER;
    }
  }
}
