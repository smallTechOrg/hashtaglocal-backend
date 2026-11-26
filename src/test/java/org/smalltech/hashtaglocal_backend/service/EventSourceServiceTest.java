package org.smalltech.hashtaglocal_backend.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.IntStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.smalltech.hashtaglocal_backend.config.TimeConstants;
import org.smalltech.hashtaglocal_backend.entity.EventSourceEntity;
import org.smalltech.hashtaglocal_backend.model.EventTypeModel;
import org.smalltech.hashtaglocal_backend.model.request.EventSourceInput;
import org.smalltech.hashtaglocal_backend.model.response.EventSourceData;
import org.smalltech.hashtaglocal_backend.model.response.EventSourceTestResult;
import org.smalltech.hashtaglocal_backend.repository.EventSourceRepository;
import org.smalltech.hashtaglocal_backend.service.scraper0.Scraper0Client;
import org.smalltech.hashtaglocal_backend.service.scraper0.Scraper0Models.EventData;
import org.smalltech.hashtaglocal_backend.service.scraper0.Scraper0Models.Item;
import org.smalltech.hashtaglocal_backend.service.scraper0.Scraper0Models.ReadResult;
import org.smalltech.hashtaglocal_backend.service.scraper0.Scraper0Properties;

@ExtendWith(MockitoExtension.class)
class EventSourceServiceTest {

  @Mock private EventSourceRepository repository;
  @Mock private Scraper0Client scraper0;
  private EventSourceService service;

  @BeforeEach
  void setUp() {
    Scraper0Properties properties = new Scraper0Properties();
    properties.setFreshnessHours(24);
    service = new EventSourceService(repository, scraper0, properties);
    lenient().when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));
  }

  private static EventSourceInput input(String siteId, String action) {
    return new EventSourceInput("Karmana", siteId, action, "  cleanups ", " ", null, null, null);
  }

  @Test
  void createAppliesTheDefaults() {
    EventSourceData created = service.create(input(" karmana-in ", null));

    assertThat(created.siteId()).isEqualTo("karmana-in");
    assertThat(created.action()).isEqualTo("fetch_events");
    assertThat(created.eventFilter()).isEqualTo("cleanups");
    assertThat(created.categoryFilter()).isNull();
    assertThat(created.includeOnline()).isFalse();
    assertThat(created.freshnessHours()).isEqualTo(24);
    assertThat(created.enabled()).isTrue();
  }

  @Test
  void createRefusesADuplicateSiteAndAction() {
    when(repository.existsBySiteIdAndAction("karmana-in", "fetch_events")).thenReturn(true);

    assertThatThrownBy(() -> service.create(input("karmana-in", "fetch_events")))
        .isInstanceOf(EventSourceService.DuplicateSourceException.class);
    verify(repository, never()).save(any());
  }

  @Test
  void updateKeepsUnsentFlagsAndRefusesDuplicates() {
    EventSourceEntity existing =
        EventSourceEntity.builder()
            .id(5L)
            .name("Old")
            .siteId("karmana-in")
            .freshnessHours(6)
            .includeOnline(true)
            .enabled(false)
            .build();
    when(repository.findById(5L)).thenReturn(Optional.of(existing));

    EventSourceData updated = service.update(5L, input("karmana-in", "fetch_events"));
    assertThat(updated.freshnessHours()).isEqualTo(6);
    assertThat(updated.includeOnline()).isTrue();
    assertThat(updated.enabled()).isFalse();
    assertThat(updated.name()).isEqualTo("Karmana");

    when(repository.existsBySiteIdAndActionAndIdNot("other-site", "fetch_events", 5L))
        .thenReturn(true);
    assertThatThrownBy(() -> service.update(5L, input("other-site", null)))
        .isInstanceOf(EventSourceService.DuplicateSourceException.class);
  }

  @Test
  void unknownIdIsNotFound() {
    when(repository.findById(9L)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> service.delete(9L))
        .isInstanceOf(EventSourceService.SourceNotFoundException.class);
  }

  @Test
  @SuppressWarnings("unchecked")
  void readSendsFiltersCategoriesIncludeOnlineAndTodayInKolkata() {
    EventSourceEntity source =
        EventSourceEntity.builder()
            .siteId("ivolunteer-in")
            .eventFilter("in-person")
            .categoryFilter("environment")
            .includeOnline(true)
            .freshnessHours(12)
            .build();
    when(scraper0.readItems(eq("ivolunteer-in"), eq("fetch_events"), anyMap(), eq(12)))
        .thenReturn(new ReadResult(null, null, 1, List.of(), true, null, null, "ok", null));

    service.read(source);

    ArgumentCaptor<Map<String, Object>> options = ArgumentCaptor.forClass(Map.class);
    verify(scraper0).readItems(eq("ivolunteer-in"), eq("fetch_events"), options.capture(), eq(12));
    assertThat(options.getValue())
        .containsEntry("event_filter", "in-person")
        .containsEntry("category_filter", "environment")
        .containsEntry("include_online", true)
        .containsEntry("from", LocalDate.now(TimeConstants.APP_ZONE).toString())
        .containsEntry(
            "categories", Arrays.stream(EventTypeModel.values()).map(Enum::name).toList());
  }

  @Test
  void blankFiltersAreNotSent() {
    Map<String, Object> options =
        EventSourceService.options(EventSourceEntity.builder().siteId("x").build());

    assertThat(options).doesNotContainKeys("event_filter", "category_filter");
    assertThat(options).containsEntry("include_online", false);
  }

  @Test
  void testReturnsTheFirst20ItemsAndImportsNothing() {
    EventSourceEntity source = EventSourceEntity.builder().id(3L).siteId("karmana-in").build();
    when(repository.findById(3L)).thenReturn(Optional.of(source));
    List<Item> items =
        IntStream.range(0, 25)
            .mapToObj(
                i ->
                    new Item(
                        "itm_" + i,
                        null,
                        new EventData(
                            "E" + i,
                            "2026-12-01T04:30:00.000Z",
                            null,
                            "Addr",
                            "https://l",
                            null,
                            "https://img",
                            "OTHER",
                            null,
                            null,
                            null,
                            i % 2 == 0,
                            null,
                            null)))
            .toList();
    when(scraper0.readItems(eq("karmana-in"), eq("fetch_events"), anyMap(), any()))
        .thenReturn(
            new ReadResult(
                null, null, 1, items, false, "2026-11-26T00:00:00Z", null, "ok", "AI down"));

    EventSourceTestResult result = service.test(3L);

    assertThat(result.itemCount()).isEqualTo(25);
    assertThat(result.items()).hasSize(20);
    assertThat(result.complete()).isFalse();
    assertThat(result.aiError()).isEqualTo("AI down");
    assertThat(result.items().get(0).isOnline()).isTrue();
    verify(repository, never()).save(any());
  }

  @Test
  void recordRunStoresLastFields() {
    EventSourceEntity source = EventSourceEntity.builder().id(3L).siteId("karmana-in").build();
    when(repository.findById(3L)).thenReturn(Optional.of(source));

    service.recordRun(3L, "error", "x".repeat(5000), 4, 0);

    assertThat(source.getLastStatus()).isEqualTo("error");
    assertThat(source.getLastError()).hasSize(EventSourceService.MAX_ERROR_LENGTH);
    assertThat(source.getLastItemCount()).isEqualTo(4);
    assertThat(source.getLastImportedCount()).isZero();
    assertThat(source.getLastRunAt()).isNotNull();
  }
}
