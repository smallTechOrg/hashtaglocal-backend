package org.smalltech.hashtaglocal_backend.integration;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.smalltech.hashtaglocal_backend.dto.ScrapeEventDTO;
import org.smalltech.hashtaglocal_backend.job.EventGeocodingJob;
import org.smalltech.hashtaglocal_backend.repository.EventRepository;
import org.smalltech.hashtaglocal_backend.service.ScrapeApiClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.reactive.server.WebTestClient;

/**
 * Integration tests for the event ingestion pipeline triggered via {@code POST
 * /api/v1/events/ingest}.
 *
 * <p>The same pipeline runs as the scheduled cron job:
 *
 * <ol>
 *   <li>Fetch events from {@link ScrapeApiClient} (mocked)
 *   <li>Import new events into the database
 *   <li>Geocode addresses via {@link EventGeocodingJob} (mocked — no Google Maps calls)
 * </ol>
 *
 * <p>These tests verify that the pipeline wires together correctly and that DB state is correct
 * after each run.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@DisplayName("POST /api/v1/events/ingest — ingestion pipeline")
class EventIngestionIntegrationTest {

  @Autowired private WebTestClient webTestClient;
  @Autowired private EventRepository eventRepository;

  // Mocked so we control what raw events the pipeline receives
  @MockitoBean private ScrapeApiClient scrapeApiClient;

  // Mocked to avoid hitting the real Google Maps API
  @MockitoBean private EventGeocodingJob eventGeocodingJob;

  @BeforeEach
  void setUp() {
    eventRepository.deleteAll();
    when(eventGeocodingJob.run()).thenReturn(new EventGeocodingJob.GeocodingJobResult(0, 0, 0, 0));
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
        .build();
  }

  @Test
  @DisplayName("Imports events from scrape service and persists them to the database")
  void importsEventsAndPersistsToDB() {
    LocalDateTime t1 = LocalDateTime.of(2026, 4, 1, 9, 0);
    LocalDateTime t2 = LocalDateTime.of(2026, 4, 2, 9, 0);
    when(scrapeApiClient.fetchEvents())
        .thenReturn(List.of(event("Trek and Plog", t1), event("Beach Cleanup", t2)));

    webTestClient.post().uri("/api/v1/events/ingest").exchange().expectStatus().isOk();

    assertEquals(2, eventRepository.count());
  }

  @Test
  @DisplayName("Geocode job is called exactly once per pipeline run")
  void geocodeJobIsCalledOncePerRun() {
    when(scrapeApiClient.fetchEvents())
        .thenReturn(List.of(event("Trek and Plog", LocalDateTime.of(2026, 4, 1, 9, 0))));

    webTestClient.post().uri("/api/v1/events/ingest").exchange().expectStatus().isOk();

    verify(eventGeocodingJob, times(1)).run();
  }

  @Test
  @DisplayName("Duplicate events are not re-inserted on repeated pipeline runs")
  void deduplicatesOnRepeatedRuns() {
    ScrapeEventDTO trek = event("Trek and Plog", LocalDateTime.of(2026, 4, 1, 9, 0));
    when(scrapeApiClient.fetchEvents()).thenReturn(List.of(trek));

    // First run — persists 1 event
    webTestClient.post().uri("/api/v1/events/ingest").exchange().expectStatus().isOk();
    assertEquals(1, eventRepository.count());

    // Second run — same event, must be deduplicated
    webTestClient.post().uri("/api/v1/events/ingest").exchange().expectStatus().isOk();
    assertEquals(1, eventRepository.count(), "Duplicate must not be re-inserted");
  }

  @Test
  @DisplayName("No events are imported and geocode is skipped when scrape returns empty")
  void skipsImportAndGeocodeWhenScrapeReturnsEmpty() {
    when(scrapeApiClient.fetchEvents()).thenReturn(List.of());

    webTestClient.post().uri("/api/v1/events/ingest").exchange().expectStatus().isOk();

    assertEquals(0, eventRepository.count());
    verify(eventGeocodingJob, never()).run();
  }

  @Test
  @DisplayName("New events added in a subsequent run are picked up and imported")
  void picksUpNewEventsOnSubsequentRun() {
    ScrapeEventDTO trek = event("Trek and Plog", LocalDateTime.of(2026, 4, 1, 9, 0));
    ScrapeEventDTO beach = event("Beach Cleanup", LocalDateTime.of(2026, 4, 2, 9, 0));

    // First run: 1 event
    when(scrapeApiClient.fetchEvents()).thenReturn(List.of(trek));
    webTestClient.post().uri("/api/v1/events/ingest").exchange().expectStatus().isOk();
    assertEquals(1, eventRepository.count());

    // Second run: same + 1 new
    when(scrapeApiClient.fetchEvents()).thenReturn(List.of(trek, beach));
    webTestClient.post().uri("/api/v1/events/ingest").exchange().expectStatus().isOk();
    assertEquals(2, eventRepository.count(), "Only the new event should be added");
  }
}
