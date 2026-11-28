package org.smalltech.hashtaglocal_backend.job;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.smalltech.hashtaglocal_backend.entity.EventSourceEntity;
import org.smalltech.hashtaglocal_backend.service.EventGeocodingService;
import org.smalltech.hashtaglocal_backend.service.EventImportService;
import org.smalltech.hashtaglocal_backend.service.EventImportService.ImportResult;
import org.smalltech.hashtaglocal_backend.service.EventSourceService;
import org.smalltech.hashtaglocal_backend.service.scraper0.Scraper0Client;
import org.smalltech.hashtaglocal_backend.service.scraper0.Scraper0Exception;
import org.smalltech.hashtaglocal_backend.service.scraper0.Scraper0Models.EventData;
import org.smalltech.hashtaglocal_backend.service.scraper0.Scraper0Models.Item;
import org.smalltech.hashtaglocal_backend.service.scraper0.Scraper0Models.ReadResult;

/**
 * Unit tests for {@link EventIngestionCronJob}: per-source read → import → record, then geocoding
 * once. No database or HTTP.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("EventIngestionCronJob — per-source ingestion")
class EventIngestionCronJobTest {

  @Mock private Scraper0Client scraper0Client;
  @Mock private EventSourceService eventSourceService;
  @Mock private EventImportService eventImportService;
  @Mock private EventGeocodingService eventGeocodingService;

  @InjectMocks private EventIngestionCronJob cronJob;

  private final EventSourceEntity everest = source(1L, "teameverest-ngo");
  private final EventSourceEntity karmana = source(2L, "karmana-in");

  @BeforeEach
  void configured() {
    org.mockito.Mockito.lenient().when(scraper0Client.isConfigured()).thenReturn(true);
    org.mockito.Mockito.lenient()
        .when(eventImportService.importItems(anyString(), anyList()))
        .thenReturn(ImportResult.NONE);
    org.mockito.Mockito.lenient()
        .when(eventGeocodingService.run())
        .thenReturn(new EventGeocodingService.GeocodingResult(0, 0, 0, 0));
  }

  private static EventSourceEntity source(Long id, String siteId) {
    return EventSourceEntity.builder().id(id).name(siteId).siteId(siteId).build();
  }

  private static Item item(String id) {
    return new Item(
        id,
        "2026-11-01T00:00:00Z",
        new EventData(
            "Event " + id,
            "2026-12-01T04:30:00.000Z",
            null,
            "Bengaluru",
            "https://e/" + id,
            null,
            "https://img/" + id,
            null,
            "OTHER",
            null,
            null,
            null,
            false,
            null,
            null));
  }

  private static ReadResult result(boolean complete, Item... items) {
    return new ReadResult("s", "fetch_events", 1, List.of(items), complete, null, null, "ok", null);
  }

  @Test
  @DisplayName("Does nothing (and logs) when scraper0 is not configured")
  void skipsWhenNotConfigured() {
    when(scraper0Client.isConfigured()).thenReturn(false);

    cronJob.run();

    verifyNoInteractions(eventSourceService, eventImportService, eventGeocodingService);
  }

  @Test
  @DisplayName("Reads and imports every enabled source and records last_* on each")
  void recordsEachSource() {
    when(eventSourceService.enabledSources()).thenReturn(List.of(everest, karmana));
    List<Item> everestItems = List.of(item("itm_a"), item("itm_b"));
    when(eventSourceService.read(everest))
        .thenReturn(result(true, everestItems.toArray(Item[]::new)));
    when(eventSourceService.read(karmana)).thenReturn(result(true, item("itm_c")));
    when(eventImportService.importItems("teameverest-ngo", everestItems))
        .thenReturn(new ImportResult(1, 0));
    when(eventImportService.importItems(eq("karmana-in"), anyList()))
        .thenReturn(new ImportResult(1, 0));

    cronJob.run();

    verify(eventSourceService).recordRun(1L, "ok", null, 2, 1);
    verify(eventSourceService).recordRun(2L, "ok", null, 1, 1);
    verify(eventGeocodingService, times(1)).run();
  }

  @Test
  @DisplayName("complete: false is recorded as partial")
  void incompleteIsPartial() {
    when(eventSourceService.enabledSources()).thenReturn(List.of(everest));
    when(eventSourceService.read(everest)).thenReturn(result(false, item("itm_a")));
    when(eventImportService.importItems(anyString(), anyList())).thenReturn(new ImportResult(0, 0));

    cronJob.run();

    verify(eventSourceService)
        .recordRun(eq(1L), eq("partial"), contains("refreshing"), eq(1), eq(0));
  }

  @Test
  @DisplayName("A failing source is recorded as error and the next source still runs")
  void oneFailingSourceDoesNotStopTheOthers() {
    when(eventSourceService.enabledSources()).thenReturn(List.of(everest, karmana));
    when(eventSourceService.read(everest))
        .thenThrow(
            new Scraper0Exception(
                404, "action_not_available", "no locked version of fetch_events"));
    when(eventSourceService.read(karmana)).thenReturn(result(true, item("itm_c")));
    when(eventImportService.importItems(eq("karmana-in"), anyList()))
        .thenReturn(new ImportResult(1, 0));

    cronJob.run();

    verify(eventSourceService)
        .recordRun(1L, "error", "action_not_available: no locked version of fetch_events", 0, 0);
    verify(eventSourceService).recordRun(2L, "ok", null, 1, 1);
    verify(eventImportService, never()).importItems(eq("teameverest-ngo"), anyList());
    verify(eventGeocodingService).run();
  }

  @Test
  @DisplayName("An import failure is recorded as error with the item count")
  void importFailureIsRecorded() {
    when(eventSourceService.enabledSources()).thenReturn(List.of(everest));
    when(eventSourceService.read(everest)).thenReturn(result(true, item("itm_a")));
    when(eventImportService.importItems(anyString(), anyList()))
        .thenThrow(new IllegalStateException("db down"));

    cronJob.run();

    verify(eventSourceService).recordRun(1L, "error", "IllegalStateException: db down", 1, 0);
  }

  @Test
  @DisplayName("A failure while recording the run does not stop the next source")
  void recordFailureIsContained() {
    when(eventSourceService.enabledSources()).thenReturn(List.of(everest, karmana));
    when(eventSourceService.read(any())).thenReturn(result(true, item("itm_a")));
    when(eventImportService.importItems(anyString(), anyList())).thenReturn(new ImportResult(1, 0));
    doThrow(new RuntimeException("db"))
        .when(eventSourceService)
        .recordRun(eq(1L), anyString(), isNull(), anyInt(), anyInt());

    cronJob.run();

    verify(eventSourceService).recordRun(2L, "ok", null, 1, 1);
  }

  @Test
  @DisplayName("Geocoding is skipped when no source returned any items")
  void geocodingSkippedWhenNothingWasRead() {
    when(eventSourceService.enabledSources()).thenReturn(List.of(everest));
    when(eventSourceService.read(everest)).thenReturn(result(true));

    cronJob.run();

    verify(eventGeocodingService, never()).run();
    verify(eventSourceService).recordRun(1L, "ok", null, 0, 0);
  }

  @Test
  @DisplayName("Broken health and AI errors are noted on an ok run")
  void notesBrokenHealthAndAiError() {
    when(eventSourceService.enabledSources()).thenReturn(List.of(everest));
    when(eventSourceService.read(everest))
        .thenReturn(
            new ReadResult(
                "s",
                "fetch_events",
                1,
                List.of(item("itm_a")),
                true,
                null,
                null,
                "broken",
                "quota"));
    when(eventImportService.importItems(anyString(), anyList())).thenReturn(new ImportResult(0, 0));

    cronJob.run();

    org.mockito.ArgumentCaptor<String> note = org.mockito.ArgumentCaptor.forClass(String.class);
    verify(eventSourceService).recordRun(eq(1L), eq("ok"), note.capture(), eq(1), eq(0));
    assertThat(note.getValue()).contains("broken").contains("quota");
  }

  @Test
  @DisplayName("Items skipped because their image could not be copied are noted on the run")
  void notesImageSkippedItems() {
    when(eventSourceService.enabledSources()).thenReturn(List.of(everest));
    when(eventSourceService.read(everest))
        .thenReturn(
            new ReadResult(
                "s",
                "fetch_events",
                1,
                List.of(item("itm_a"), item("itm_b"), item("itm_c")),
                true,
                null,
                null,
                "ok",
                null));
    when(eventImportService.importItems(anyString(), anyList())).thenReturn(new ImportResult(1, 2));

    cronJob.run();

    org.mockito.ArgumentCaptor<String> note = org.mockito.ArgumentCaptor.forClass(String.class);
    verify(eventSourceService).recordRun(eq(1L), eq("ok"), note.capture(), eq(3), eq(1));
    assertThat(note.getValue()).contains("2 items skipped").contains("image");
  }
}
