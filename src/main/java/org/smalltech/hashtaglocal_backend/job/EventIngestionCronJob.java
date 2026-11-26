package org.smalltech.hashtaglocal_backend.job;

import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.smalltech.hashtaglocal_backend.entity.EventSourceEntity;
import org.smalltech.hashtaglocal_backend.service.EventGeocodingService;
import org.smalltech.hashtaglocal_backend.service.EventImportService;
import org.smalltech.hashtaglocal_backend.service.EventSourceService;
import org.smalltech.hashtaglocal_backend.service.scraper0.Scraper0Client;
import org.smalltech.hashtaglocal_backend.service.scraper0.Scraper0Exception;
import org.smalltech.hashtaglocal_backend.service.scraper0.Scraper0Models.ReadResult;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Event ingestion: reads every enabled event source ({@code event_sources}) from scraper0, imports
 * the new events, then geocodes any events whose address has not yet been resolved to a lat/lng.
 *
 * <p>Each source is read and imported on its own: a source that fails is recorded as {@code error}
 * on its row and the run goes on with the next one. {@code complete: false} (scraper0 was still
 * refreshing at the timeout) is recorded as {@code partial}.
 *
 * <pre>
 * scraper0.base-url:  ${SCRAPER0_BASE_URL}   # empty = ingestion off
 * events.scrape.cron: ${EVENTS_SCRAPE_CRON}  # Spring cron (default every 30 minutes); '-' = off
 * </pre>
 *
 * <p>On Cloud Run the in-process timer is off and Cloud Scheduler calls {@code POST
 * /jobs/event-ingestion}; the Ops Dashboard can also start a run ({@code POST
 * /admin/event-sources/run}).
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class EventIngestionCronJob {

  private final Scraper0Client scraper0Client;
  private final EventSourceService eventSourceService;
  private final EventImportService eventImportService;
  private final EventGeocodingService eventGeocodingService;

  /** How one source's run went. */
  public record SourceRun(
      Long sourceId, String siteId, String status, int itemCount, int importedCount) {}

  @Scheduled(cron = "${events.scrape.cron}")
  public void run() {
    if (!scraper0Client.isConfigured()) {
      log.warn("Event ingestion skipped: scraper0.base-url (SCRAPER0_BASE_URL) is not set");
      return;
    }
    List<EventSourceEntity> sources = eventSourceService.enabledSources();
    log.info("Event ingestion started: {} enabled sources", sources.size());

    List<SourceRun> runs = new ArrayList<>();
    for (EventSourceEntity source : sources) {
      runs.add(ingest(source));
    }

    int items = runs.stream().mapToInt(SourceRun::itemCount).sum();
    int imported = runs.stream().mapToInt(SourceRun::importedCount).sum();
    log.info("Event ingestion read {} items and imported {} new events", items, imported);

    if (items == 0) {
      log.info("No events returned by any source — skipping geocoding");
      return;
    }
    EventGeocodingService.GeocodingResult geocodeResult = eventGeocodingService.run();
    log.info("Geocoding complete: {}", geocodeResult);
  }

  /** Reads and imports one source and records the outcome on it; never throws. */
  SourceRun ingest(EventSourceEntity source) {
    String label = source.getSiteId() + "/" + source.getAction();
    int itemCount = 0;
    try {
      ReadResult result = eventSourceService.read(source);
      itemCount = result.itemsOrEmpty().size();
      int imported = eventImportService.importItems(source.getSiteId(), result.itemsOrEmpty());

      String status =
          result.complete() ? EventSourceEntity.STATUS_OK : EventSourceEntity.STATUS_PARTIAL;
      String note = note(result);
      eventSourceService.recordRun(source.getId(), status, note, itemCount, imported);
      log.info(
          "Event source {}: {} items, {} imported, status {}{}",
          label,
          itemCount,
          imported,
          status,
          note == null ? "" : " (" + note + ")");
      return new SourceRun(source.getId(), source.getSiteId(), status, itemCount, imported);
    } catch (Exception e) {
      String error =
          e instanceof Scraper0Exception s0
              ? s0.describe()
              : e.getClass().getSimpleName() + ": " + e.getMessage();
      log.error("Event source {} failed: {}", label, error);
      try {
        eventSourceService.recordRun(
            source.getId(), EventSourceEntity.STATUS_ERROR, error, itemCount, 0);
      } catch (Exception recordError) {
        log.error(
            "Could not record the run of event source {}: {}", label, recordError.getMessage());
      }
      return new SourceRun(
          source.getId(), source.getSiteId(), EventSourceEntity.STATUS_ERROR, itemCount, 0);
    }
  }

  /** Warnings worth showing next to an ok/partial run, or null. */
  private static String note(ReadResult result) {
    List<String> notes = new ArrayList<>();
    if (!result.complete()) {
      notes.add("scraper0 was still refreshing at the timeout");
    }
    if ("broken".equals(result.health())) {
      notes.add("scraper0 reports the action as broken; these are stored items");
    }
    if (result.aiError() != null && !result.aiError().isBlank()) {
      notes.add("AI filter unavailable: " + result.aiError());
    }
    return notes.isEmpty() ? null : String.join("; ", notes);
  }
}
