package org.smalltech.hashtaglocal_backend.service;

import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.smalltech.hashtaglocal_backend.dto.ScrapeEventDTO;
import org.smalltech.hashtaglocal_backend.entity.EventEntity;
import org.smalltech.hashtaglocal_backend.entity.MediaEntity;
import org.smalltech.hashtaglocal_backend.model.EventPortalModel;
import org.smalltech.hashtaglocal_backend.model.EventTypeModel;
import org.smalltech.hashtaglocal_backend.repository.EventRepository;
import org.springframework.stereotype.Service;

/**
 * Handles importing events from the scrape service JSON response.
 *
 * <p>Key behaviours:
 *
 * <ul>
 *   <li>Events with a blank {@code name} or null {@code startTime} are silently skipped.
 *   <li>Deduplication: if an event with the same {@code name} + {@code startTime} already exists in
 *       the database it is skipped — no duplicates are inserted.
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

  /**
   * Processes a list of events from the scrape service, deduplicates against the database, and
   * saves each new event (with its {@code PENDING} approval row) in its own transaction.
   *
   * <p>Events are persisted independently, so a single bad row — a constraint violation, a failed
   * image upload, etc. — only skips that event instead of rolling back the whole batch. A missing
   * address does <b>not</b> drop the event: it is kept for admin review and geocoded later once an
   * address is supplied via the ops portal.
   *
   * @param scrapeEvents raw event DTOs from the scrape service response
   * @return the number of events actually saved (duplicates and failures excluded)
   */
  public int importFromScrapeResponse(List<ScrapeEventDTO> scrapeEvents) {
    if (scrapeEvents == null || scrapeEvents.isEmpty()) {
      return 0;
    }

    int imported = 0;

    for (ScrapeEventDTO dto : scrapeEvents) {
      try {
        if (dto.getName() == null || dto.getName().isBlank()) {
          log.debug("Skipping event with blank name");
          continue;
        }
        if (dto.getStartTime() == null) {
          log.debug("Skipping event '{}' — startTime is null", dto.getName());
          continue;
        }
        if (dto.getImage() == null || dto.getImage().isBlank()) {
          log.debug("Skipping event '{}' — image is null or blank", dto.getName());
          continue;
        }
        // fromString() returns null for portals it doesn't recognise. portal is a NOT NULL
        // column, so letting a null through would fail the insert for this event.
        if (EventPortalModel.fromString(dto.getPortal()) == null) {
          log.warn(
              "Skipping event '{}' — unrecognized portal '{}'", dto.getName(), dto.getPortal());
          continue;
        }
        if (eventRepository.existsByNameAndStartTime(dto.getName(), dto.getStartTime())) {
          log.debug("Skipping duplicate event '{}' at {}", dto.getName(), dto.getStartTime());
          continue;
        }

        // Download the CDN image, upload to GCS, and persist a MediaEntity row.
        // Skip the event if the image can't be stored — an event without a valid image is unusable.
        MediaEntity media = eventImageService.downloadAndStore(dto.getImage());
        if (media == null) {
          log.debug("Skipping event '{}' — image download/upload failed", dto.getName());
          continue;
        }

        // Persist this event (plus its PENDING approval) in its own transaction. Isolating each
        // save means one problematic event never takes the rest of the batch down with it.
        eventService.saveWithPendingApproval(toEntity(dto, media));
        imported++;
      } catch (Exception e) {
        log.warn("Skipping event '{}' due to error: {}", dto.getName(), e.getMessage());
      }
    }

    log.info(
        "Imported {} new events ({} received, {} skipped)",
        imported,
        scrapeEvents.size(),
        scrapeEvents.size() - imported);
    return imported;
  }

  private EventEntity toEntity(ScrapeEventDTO dto, MediaEntity media) {
    return EventEntity.builder()
        .name(dto.getName())
        .organisation(dto.getOrganisation())
        .portal(EventPortalModel.fromString(dto.getPortal()))
        .type(parseEventType(dto.getType()))
        .startTime(dto.getStartTime())
        .endTime(dto.getEndTime())
        .address(normalizeAddress(dto.getAddress()))
        .link(stripUtmParams(dto.getLink()))
        .media(media)
        .build();
  }

  /**
   * Normalises the scraped address: trims surrounding whitespace and collapses a blank or missing
   * value to {@code null}. Storing null (rather than an empty string) keeps the geocoding query
   * ({@code findByLocationIsNullAndAddressIsNotNull}) from repeatedly picking up un-geocodable
   * events.
   */
  private String normalizeAddress(String address) {
    if (address == null || address.isBlank()) {
      return null;
    }
    return address.strip();
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
