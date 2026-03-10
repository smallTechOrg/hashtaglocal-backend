package org.smalltech.hashtaglocal_backend.job;

import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.*;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.smalltech.hashtaglocal_backend.dto.ScrapeEventDTO;
import org.smalltech.hashtaglocal_backend.service.EventGeocodingService;
import org.smalltech.hashtaglocal_backend.service.EventImportService;
import org.smalltech.hashtaglocal_backend.service.ScrapeApiClient;

/**
 * Unit tests for {@link EventIngestionCronJob} orchestration logic.
 *
 * <p>Verifies the pipeline control flow: scrape → import → geocode. Does not touch the database or
 * any HTTP endpoint.
 *
 * <p>Parameterised scenarios (non-empty scrape) are driven from {@code
 * event-ingestion-cron-job-test-cases.json}; edge-case tests (empty list, exact count) are inline.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("EventIngestionCronJob — pipeline orchestration")
class EventIngestionCronJobTest {

  @Mock private ScrapeApiClient scrapeApiClient;
  @Mock private EventImportService eventImportService;
  @Mock private EventGeocodingService eventGeocodingService;

  @InjectMocks private EventIngestionCronJob cronJob;

  private static final ObjectMapper MAPPER =
      new ObjectMapper().configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

  private ScrapeEventDTO event(String name) {
    return ScrapeEventDTO.builder()
        .name(name)
        .organisation("Test Org")
        .startTime(LocalDateTime.of(2026, 4, 1, 9, 0))
        .build();
  }

  private EventGeocodingService.GeocodingResult geocodeResult(int total, int success) {
    return new EventGeocodingService.GeocodingResult(total, success, 0, 0);
  }

  // ---------------------------------------------------------------------------
  // Records matching event-ingestion-cron-job-test-cases.json
  // ---------------------------------------------------------------------------

  record ScrapeItem(String name, @JsonProperty("start_time") String startTime) {}

  record CronJobTestCase(
      @JsonProperty("test_class") String testClass,
      String scenario,
      String description,
      @JsonProperty("scrape_returns") List<ScrapeItem> scrapeReturns,
      @JsonProperty("expected") Map<String, Object> expected) {}

  // ---------------------------------------------------------------------------
  // Parameterised: non-empty scrape → import and geocode are both called
  // ---------------------------------------------------------------------------

  static Stream<Arguments> nonEmptyScrapeScenarios() throws IOException {
    List<CronJobTestCase> cases =
        MAPPER.readValue(
            EventIngestionCronJobTest.class.getResourceAsStream(
                "/event-ingestion-cron-job-test-cases.json"),
            new TypeReference<>() {});
    return cases.stream()
        .filter(tc -> tc.testClass().startsWith("EventIngestionCronJobTest"))
        .filter(tc -> tc.scrapeReturns() != null && !tc.scrapeReturns().isEmpty())
        .filter(
            tc -> {
              Object importCalled = tc.expected().get("import_called");
              return importCalled == null || Boolean.TRUE.equals(importCalled);
            })
        .map(tc -> Arguments.of(tc.scenario(), tc.description(), tc.scrapeReturns().size()));
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("nonEmptyScrapeScenarios")
  @DisplayName("Non-empty scrape — import then geocode are both invoked")
  void importAndGeocodeCalledForNonEmptyScrape(
      String scenario, String description, int eventCount) {
    List<ScrapeEventDTO> events =
        Stream.iterate(0, i -> i + 1).limit(eventCount).map(i -> event("Event " + i)).toList();

    when(scrapeApiClient.fetchEvents()).thenReturn(events);
    when(eventGeocodingService.run()).thenReturn(geocodeResult(eventCount, eventCount));

    cronJob.run();

    verify(eventImportService).importFromScrapeResponse(events);
    verify(eventGeocodingService).run();
  }

  // ---------------------------------------------------------------------------
  // Inline tests for specific behaviours
  // ---------------------------------------------------------------------------

  @Test
  @DisplayName("Passes the exact scrape events list to the import service")
  void passesExactEventListToImportService() {
    ScrapeEventDTO trek = event("Trek and Plog");
    ScrapeEventDTO beach = event("Beach Cleanup");
    List<ScrapeEventDTO> events = List.of(trek, beach);

    when(scrapeApiClient.fetchEvents()).thenReturn(events);
    when(eventGeocodingService.run()).thenReturn(geocodeResult(0, 0));

    cronJob.run();

    verify(eventImportService).importFromScrapeResponse(List.of(trek, beach));
  }

  @Test
  @DisplayName("Skips import and geocode entirely when scrape returns no events")
  void skipsImportAndGeocodeWhenScrapeIsEmpty() {
    when(scrapeApiClient.fetchEvents()).thenReturn(List.of());

    cronJob.run();

    verifyNoInteractions(eventImportService);
    verifyNoInteractions(eventGeocodingService);
  }

  @Test
  @DisplayName("Geocode still runs even when import saves 0 events (all duplicates)")
  void geocodeRunsEvenWhenImportSavesZero() {
    when(scrapeApiClient.fetchEvents()).thenReturn(List.of(event("Trek and Plog")));
    when(eventImportService.importFromScrapeResponse(anyList())).thenReturn(0);
    when(eventGeocodingService.run()).thenReturn(geocodeResult(0, 0));

    cronJob.run();

    verify(eventGeocodingService).run();
  }

  @Test
  @DisplayName("Geocode is called exactly once per run regardless of import count")
  void geocodeIsCalledExactlyOnce() {
    when(scrapeApiClient.fetchEvents()).thenReturn(List.of(event("Event A"), event("Event B")));
    when(eventGeocodingService.run()).thenReturn(geocodeResult(2, 2));

    cronJob.run();

    verify(eventGeocodingService, times(1)).run();
  }
}
