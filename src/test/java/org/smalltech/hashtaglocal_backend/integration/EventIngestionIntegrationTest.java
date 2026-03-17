package org.smalltech.hashtaglocal_backend.integration;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.smalltech.hashtaglocal_backend.dto.ScrapeEventDTO;
import org.smalltech.hashtaglocal_backend.entity.MediaEntity;
import org.smalltech.hashtaglocal_backend.job.EventIngestionCronJob;
import org.smalltech.hashtaglocal_backend.model.MediaTypeModel;
import org.smalltech.hashtaglocal_backend.repository.EventRepository;
import org.smalltech.hashtaglocal_backend.repository.MediaRepository;
import org.smalltech.hashtaglocal_backend.service.EventGeocodingService;
import org.smalltech.hashtaglocal_backend.service.EventImageService;
import org.smalltech.hashtaglocal_backend.service.ScrapeApiClient;
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
 *   <li>Fetch events from {@link ScrapeApiClient} (mocked)
 *   <li>Import new events into the database
 *   <li>Geocode addresses via {@link EventGeocodingService} (mocked — no Google Maps calls)
 * </ol>
 */
@SpringBootTest
@ActiveProfiles("test")
@DisplayName("EventIngestionCronJob — ingestion pipeline")
class EventIngestionIntegrationTest {

  @Autowired private EventIngestionCronJob eventIngestionCronJob;
  @Autowired private EventRepository eventRepository;
  @Autowired private MediaRepository mediaRepository;

  // Mocked so we control what raw events the pipeline receives
  @MockitoBean private ScrapeApiClient scrapeApiClient;

  // Mocked to avoid hitting the real Google Maps API
  @MockitoBean private EventGeocodingService eventGeocodingService;

  // Mocked to avoid hitting external image URLs and GCS
  @MockitoBean private EventImageService eventImageService;

  @BeforeEach
  void setUp() {
    eventRepository.deleteAll();
    mediaRepository.deleteAll();
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

  private ScrapeEventDTO event(String name, LocalDateTime startTime) {
    return ScrapeEventDTO.builder()
        .name(name)
        .organisation("Test Org")
        .portal("Team everest")
        .type("TREKANDPLOG")
        .startTime(startTime)
        .address("Lalbagh Main gate, Bengaluru")
        .link("https://example.com/event")
        .image("https://example.com/image.jpg")
        .build();
  }

  @Test
  @DisplayName("Imports events from scrape service and persists them to the database")
  void importsEventsAndPersistsToDB() {
    LocalDateTime t1 = LocalDateTime.of(2026, 4, 1, 9, 0);
    LocalDateTime t2 = LocalDateTime.of(2026, 4, 2, 9, 0);
    when(scrapeApiClient.fetchEvents())
        .thenReturn(List.of(event("Trek and Plog", t1), event("Beach Cleanup", t2)));

    eventIngestionCronJob.run();

    assertEquals(2, eventRepository.count());
  }

  @Test
  @DisplayName("Geocode job is called exactly once per pipeline run")
  void geocodeJobIsCalledOncePerRun() {
    when(scrapeApiClient.fetchEvents())
        .thenReturn(List.of(event("Trek and Plog", LocalDateTime.of(2026, 4, 1, 9, 0))));

    eventIngestionCronJob.run();

    verify(eventGeocodingService, times(1)).run();
  }

  @Test
  @DisplayName("Duplicate events are not re-inserted on repeated pipeline runs")
  void deduplicatesOnRepeatedRuns() {
    ScrapeEventDTO trek = event("Trek and Plog", LocalDateTime.of(2026, 4, 1, 9, 0));
    when(scrapeApiClient.fetchEvents()).thenReturn(List.of(trek));

    // First run — persists 1 event
    eventIngestionCronJob.run();
    assertEquals(1, eventRepository.count());

    // Second run — same event, must be deduplicated
    eventIngestionCronJob.run();
    assertEquals(1, eventRepository.count(), "Duplicate must not be re-inserted");
  }

  @Test
  @DisplayName("No events are imported and geocode is skipped when scrape returns empty")
  void skipsImportAndGeocodeWhenScrapeReturnsEmpty() {
    when(scrapeApiClient.fetchEvents()).thenReturn(List.of());

    eventIngestionCronJob.run();

    assertEquals(0, eventRepository.count());
    verify(eventGeocodingService, never()).run();
  }

  @Test
  @DisplayName("New events added in a subsequent run are picked up and imported")
  void picksUpNewEventsOnSubsequentRun() {
    ScrapeEventDTO trek = event("Trek and Plog", LocalDateTime.of(2026, 4, 1, 9, 0));
    ScrapeEventDTO beach = event("Beach Cleanup", LocalDateTime.of(2026, 4, 2, 9, 0));

    // First run: 1 event
    when(scrapeApiClient.fetchEvents()).thenReturn(List.of(trek));
    eventIngestionCronJob.run();
    assertEquals(1, eventRepository.count());

    // Second run: same + 1 new
    when(scrapeApiClient.fetchEvents()).thenReturn(List.of(trek, beach));
    eventIngestionCronJob.run();
    assertEquals(2, eventRepository.count(), "Only the new event should be added");
  }
}
