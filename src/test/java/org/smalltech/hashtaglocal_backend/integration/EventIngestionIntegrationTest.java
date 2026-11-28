package org.smalltech.hashtaglocal_backend.integration;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.*;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.smalltech.hashtaglocal_backend.entity.EventSourceEntity;
import org.smalltech.hashtaglocal_backend.entity.MediaEntity;
import org.smalltech.hashtaglocal_backend.job.EventIngestionCronJob;
import org.smalltech.hashtaglocal_backend.model.EventPortalModel;
import org.smalltech.hashtaglocal_backend.model.MediaTypeModel;
import org.smalltech.hashtaglocal_backend.repository.EventApprovalRepository;
import org.smalltech.hashtaglocal_backend.repository.EventRepository;
import org.smalltech.hashtaglocal_backend.repository.EventSourceRepository;
import org.smalltech.hashtaglocal_backend.repository.MediaRepository;
import org.smalltech.hashtaglocal_backend.service.EventGeocodingService;
import org.smalltech.hashtaglocal_backend.service.EventImageService;
import org.smalltech.hashtaglocal_backend.service.scraper0.Scraper0Client;
import org.smalltech.hashtaglocal_backend.service.scraper0.Scraper0Models.EventData;
import org.smalltech.hashtaglocal_backend.service.scraper0.Scraper0Models.Item;
import org.smalltech.hashtaglocal_backend.service.scraper0.Scraper0Models.ReadResult;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

/**
 * Integration tests for the {@link EventIngestionCronJob}.
 *
 * <p>Calls the cron job directly (no HTTP) to verify the full pipeline:
 *
 * <ol>
 *   <li>Read each enabled event source through {@link Scraper0Client} (mocked)
 *   <li>Import new events into the database and record the run on the source
 *   <li>Geocode addresses via {@link EventGeocodingService} (mocked — no Google Maps calls)
 * </ol>
 */
@SpringBootTest
@ActiveProfiles("test")
@DisplayName("EventIngestionCronJob — ingestion pipeline")
class EventIngestionIntegrationTest {

  @Autowired private EventIngestionCronJob eventIngestionCronJob;
  @Autowired private EventRepository eventRepository;
  @Autowired private EventApprovalRepository eventApprovalRepository;
  @Autowired private EventSourceRepository eventSourceRepository;
  @Autowired private MediaRepository mediaRepository;

  // Mocked so we control what each source returns
  @MockitoBean private Scraper0Client scraper0Client;

  // Mocked to avoid hitting the real Google Maps API
  @MockitoBean private EventGeocodingService eventGeocodingService;

  // Mocked to avoid hitting external image URLs and GCS
  @MockitoBean private EventImageService eventImageService;

  private EventSourceEntity source;

  @BeforeEach
  void setUp() {
    eventApprovalRepository.deleteAll();
    eventRepository.deleteAll();
    mediaRepository.deleteAll();
    eventSourceRepository.deleteAll();
    source =
        eventSourceRepository.save(
            EventSourceEntity.builder().name("Team Everest").siteId("teameverest-ngo").build());
    when(scraper0Client.isConfigured()).thenReturn(true);
    when(eventGeocodingService.run())
        .thenReturn(new EventGeocodingService.GeocodingResult(0, 0, 0, 0));
    MediaEntity savedMedia =
        mediaRepository.save(
            MediaEntity.builder()
                .type(MediaTypeModel.PHOTO)
                .url("https://example.com/image.jpg")
                .build());
    when(eventImageService.downloadAndStore(any())).thenReturn(savedMedia);
  }

  private Item item(String id, String name, String startTime) {
    return new Item(
        id,
        "2026-03-01T00:00:00Z",
        new EventData(
            name,
            startTime,
            null,
            "Lalbagh Main gate, Bengaluru",
            "https://example.com/event/" + id,
            "Test Org",
            "https://example.com/image.jpg",
            null,
            "TREKANDPLOG",
            null,
            null,
            null,
            false,
            null,
            null));
  }

  private void scraperReturns(Item... items) {
    when(scraper0Client.readItems(
            eq("teameverest-ngo"), eq("fetch_events"), any(Map.class), anyInt()))
        .thenReturn(
            new ReadResult(
                "teameverest-ngo",
                "fetch_events",
                1,
                List.of(items),
                true,
                null,
                null,
                "ok",
                null));
  }

  @Test
  @DisplayName("Imports items from the source, stores external_id and records the run")
  void importsEventsAndPersistsToDB() {
    scraperReturns(
        item("itm_1", "Trek and Plog", "2026-04-01T03:30:00.000Z"),
        item("itm_2", "Beach Cleanup", "2026-04-02T03:30:00.000Z"));

    eventIngestionCronJob.run();

    assertEquals(2, eventRepository.count());
    assertEquals(2, eventApprovalRepository.count());
    var saved = eventRepository.findAll();
    assertTrue(saved.stream().allMatch(e -> e.getPortal() == EventPortalModel.TEAMEVEREST));
    assertTrue(saved.stream().allMatch(e -> "teameverest-ngo".equals(e.getSourceSiteId())));
    assertTrue(saved.stream().anyMatch(e -> "itm_1".equals(e.getExternalId())));
    EventSourceEntity recorded = eventSourceRepository.findById(source.getId()).orElseThrow();
    assertEquals("ok", recorded.getLastStatus());
    assertEquals(2, recorded.getLastItemCount());
    assertEquals(2, recorded.getLastImportedCount());
    assertNotNull(recorded.getLastRunAt());
  }

  @Test
  @DisplayName("Geocode job is called exactly once per pipeline run")
  void geocodeJobIsCalledOncePerRun() {
    scraperReturns(item("itm_1", "Trek and Plog", "2026-04-01T03:30:00.000Z"));

    eventIngestionCronJob.run();

    verify(eventGeocodingService, times(1)).run();
  }

  @Test
  @DisplayName("Items already imported (same external_id) are not re-inserted on repeated runs")
  void deduplicatesOnRepeatedRuns() {
    scraperReturns(item("itm_1", "Trek and Plog", "2026-04-01T03:30:00.000Z"));

    eventIngestionCronJob.run();
    assertEquals(1, eventRepository.count());

    eventIngestionCronJob.run();
    assertEquals(1, eventRepository.count(), "Duplicate must not be re-inserted");
    assertEquals(
        0, eventSourceRepository.findById(source.getId()).orElseThrow().getLastImportedCount());
  }

  @Test
  @DisplayName("No events are imported and geocode is skipped when the source returns nothing")
  void skipsImportAndGeocodeWhenScrapeReturnsEmpty() {
    scraperReturns();

    eventIngestionCronJob.run();

    assertEquals(0, eventRepository.count());
    verify(eventGeocodingService, never()).run();
  }

  @Test
  @DisplayName("New items added in a subsequent run are picked up and imported")
  void picksUpNewEventsOnSubsequentRun() {
    Item trek = item("itm_1", "Trek and Plog", "2026-04-01T03:30:00.000Z");
    Item beach = item("itm_2", "Beach Cleanup", "2026-04-02T03:30:00.000Z");

    scraperReturns(trek);
    eventIngestionCronJob.run();
    assertEquals(1, eventRepository.count());

    scraperReturns(trek, beach);
    eventIngestionCronJob.run();
    assertEquals(2, eventRepository.count(), "Only the new event should be added");
  }
}
