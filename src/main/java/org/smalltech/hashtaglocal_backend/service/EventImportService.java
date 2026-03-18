package org.smalltech.hashtaglocal_backend.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
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

  private final EventService eventService;
  private final EventRepository eventRepository;
  private final EventImageService eventImageService;

  /**
   * Processes a list of events from the scrape service, deduplicates against the database, and
   * bulk-saves all new events in a single transaction.
   *
   * @param scrapeEvents raw event DTOs from the scrape service response
   * @return the number of events actually saved (duplicates excluded)
   */
  public int importFromScrapeResponse(List<ScrapeEventDTO> scrapeEvents) {
    if (scrapeEvents == null || scrapeEvents.isEmpty()) {
      return 0;
    }

    List<EventEntity> toSave = new ArrayList<>();

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

        toSave.add(toEntity(dto, media));
      } catch (Exception e) {
        log.warn("Skipping event '{}' due to error: {}", dto.getName(), e.getMessage());
      }
    }

    eventService.saveAll(toSave);
    log.info(
        "Imported {} new events ({} received, {} skipped)",
        toSave.size(),
        scrapeEvents.size(),
        scrapeEvents.size() - toSave.size());
    return toSave.size();
  }

  private EventEntity toEntity(ScrapeEventDTO dto, MediaEntity media) {
    return EventEntity.builder()
        .name(dto.getName())
        .organisation(dto.getOrganisation())
        .portal(EventPortalModel.fromString(dto.getPortal()))
        .type(parseEventType(dto.getType()))
        .startTime(dto.getStartTime())
        .endTime(dto.getEndTime())
        .address(dto.getAddress())
        .link(dto.getLink())
        .media(media)
        .build();
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
