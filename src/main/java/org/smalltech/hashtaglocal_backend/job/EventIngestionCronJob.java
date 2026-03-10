package org.smalltech.hashtaglocal_backend.job;

import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.smalltech.hashtaglocal_backend.dto.ScrapeEventDTO;
import org.smalltech.hashtaglocal_backend.service.EventGeocodingService;
import org.smalltech.hashtaglocal_backend.service.EventImportService;
import org.smalltech.hashtaglocal_backend.service.ScrapeApiClient;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Scheduled job that pulls raw events from the scrape service, imports them into the database, and
 * then geocodes any events whose address has not yet been resolved to a lat/lng.
 *
 * <p>The cron expression and scrape URL are configurable:
 *
 * <pre>
 * events:
 *   scrape:
 *     url:  ${EVENTS_SCRAPE_URL}   # URL of the external scrape service
 *     cron: ${EVENTS_SCRAPE_CRON}  # Spring cron expression (default: every 2 minutes)
 * </pre>
 *
 * <p>Set {@code events.scrape.cron=-} to disable the job without removing it.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class EventIngestionCronJob {

  private final ScrapeApiClient scrapeApiClient;
  private final EventImportService eventImportService;
  private final EventGeocodingService eventGeocodingService;

  @Scheduled(cron = "${events.scrape.cron}")
  public void run() {
    log.info("Event ingestion cron job started");

    // Step 1: fetch raw events from the scrape service
    List<ScrapeEventDTO> rawEvents = scrapeApiClient.fetchEvents();

    if (rawEvents.isEmpty()) {
      log.info("No events returned from scrape service — skipping import and geocoding");
      return;
    }

    // Step 2: import new events into the database (deduplication is handled inside)
    int imported = eventImportService.importFromScrapeResponse(rawEvents);
    log.info("Imported {} new events", imported);

    // Step 3: geocode any events that still lack a resolved location
    EventGeocodingService.GeocodingResult geocodeResult = eventGeocodingService.run();
    log.info("Geocoding complete: {}", geocodeResult);
  }
}
