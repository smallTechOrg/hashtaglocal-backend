package org.smalltech.hashtaglocal_backend.job;

import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.*;

import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.smalltech.hashtaglocal_backend.dto.ScrapeEventDTO;
import org.smalltech.hashtaglocal_backend.service.EventImportService;
import org.smalltech.hashtaglocal_backend.service.ScrapeApiClient;

/**
 * Unit tests for {@link EventIngestionCronJob} orchestration logic.
 *
 * <p>Verifies the pipeline control flow: scrape → import → geocode. Does not touch the database or
 * any HTTP endpoint.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("EventIngestionCronJob — pipeline orchestration")
class EventIngestionCronJobTest {

  @Mock private ScrapeApiClient scrapeApiClient;
  @Mock private EventImportService eventImportService;
  @Mock private EventGeocodingJob eventGeocodingJob;

  @InjectMocks private EventIngestionCronJob cronJob;

  private ScrapeEventDTO event(String name) {
    return ScrapeEventDTO.builder()
        .name(name)
        .organisation("Test Org")
        .startTime(LocalDateTime.of(2026, 4, 1, 9, 0))
        .build();
  }

  private EventGeocodingJob.GeocodingJobResult geocodeResult(int total, int success) {
    return new EventGeocodingJob.GeocodingJobResult(total, success, 0, 0);
  }

  @Test
  @DisplayName("Calls import then geocode when scrape returns events")
  void callsImportAndGeocodeWhenEventsAreReturned() {
    List<ScrapeEventDTO> events = List.of(event("Trek and Plog"), event("Beach Cleanup"));
    when(scrapeApiClient.fetchEvents()).thenReturn(events);
    when(eventImportService.importFromScrapeResponse(events)).thenReturn(2);
    when(eventGeocodingJob.run()).thenReturn(geocodeResult(2, 2));

    cronJob.run();

    verify(eventImportService).importFromScrapeResponse(events);
    verify(eventGeocodingJob).run();
  }

  @Test
  @DisplayName("Passes the exact scrape events list to the import service")
  void passesExactEventListToImportService() {
    ScrapeEventDTO trek = event("Trek and Plog");
    ScrapeEventDTO beach = event("Beach Cleanup");
    List<ScrapeEventDTO> events = List.of(trek, beach);

    when(scrapeApiClient.fetchEvents()).thenReturn(events);
    when(eventGeocodingJob.run()).thenReturn(geocodeResult(0, 0));

    cronJob.run();

    verify(eventImportService).importFromScrapeResponse(List.of(trek, beach));
  }

  @Test
  @DisplayName("Skips import and geocode entirely when scrape returns no events")
  void skipsImportAndGeocodeWhenScrapeIsEmpty() {
    when(scrapeApiClient.fetchEvents()).thenReturn(List.of());

    cronJob.run();

    verifyNoInteractions(eventImportService);
    verifyNoInteractions(eventGeocodingJob);
  }

  @Test
  @DisplayName("Geocode still runs even when import saves 0 events (all duplicates)")
  void geocodeRunsEvenWhenImportSavesZero() {
    when(scrapeApiClient.fetchEvents()).thenReturn(List.of(event("Trek and Plog")));
    when(eventImportService.importFromScrapeResponse(anyList())).thenReturn(0);
    when(eventGeocodingJob.run()).thenReturn(geocodeResult(0, 0));

    cronJob.run();

    verify(eventGeocodingJob).run();
  }

  @Test
  @DisplayName("Geocode is called exactly once per run regardless of import count")
  void geocodeIsCalledExactlyOnce() {
    when(scrapeApiClient.fetchEvents()).thenReturn(List.of(event("Event A"), event("Event B")));
    when(eventGeocodingJob.run()).thenReturn(geocodeResult(2, 2));

    cronJob.run();

    verify(eventGeocodingJob, times(1)).run();
  }
}
