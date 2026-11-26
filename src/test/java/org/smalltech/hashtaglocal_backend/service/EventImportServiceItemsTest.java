package org.smalltech.hashtaglocal_backend.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.smalltech.hashtaglocal_backend.entity.EventApprovalEntity;
import org.smalltech.hashtaglocal_backend.entity.EventEntity;
import org.smalltech.hashtaglocal_backend.entity.MediaEntity;
import org.smalltech.hashtaglocal_backend.model.EventApprovalStatus;
import org.smalltech.hashtaglocal_backend.model.EventPortalModel;
import org.smalltech.hashtaglocal_backend.model.EventTypeModel;
import org.smalltech.hashtaglocal_backend.model.MediaTypeModel;
import org.smalltech.hashtaglocal_backend.repository.EventApprovalRepository;
import org.smalltech.hashtaglocal_backend.repository.EventRepository;
import org.smalltech.hashtaglocal_backend.service.scraper0.Scraper0Models.EventData;
import org.smalltech.hashtaglocal_backend.service.scraper0.Scraper0Models.Item;

/** {@link EventImportService#importItems}: importing the items of a scraper0 event source. */
@ExtendWith(MockitoExtension.class)
@DisplayName("EventImportService — scraper0 items")
class EventImportServiceItemsTest {

  @Mock private EventService eventService;
  @Mock private EventRepository eventRepository;
  @Mock private EventImageService eventImageService;
  @Mock private EventApprovalRepository eventApprovalRepository;

  @InjectMocks private EventImportService eventImportService;

  private static final String START = "2026-12-01T04:30:00.000Z";
  private static final LocalDateTime START_UTC = LocalDateTime.of(2026, 12, 1, 4, 30);

  @BeforeEach
  void stubs() {
    MediaEntity media =
        MediaEntity.builder().type(MediaTypeModel.PHOTO).url("https://gcs/image.jpg").build();
    lenient().when(eventImageService.downloadAndStore(any())).thenReturn(media);
    lenient()
        .when(eventService.saveAll(anyList()))
        .thenAnswer(
            inv -> {
              List<EventEntity> events = inv.getArgument(0);
              long id = 100;
              for (EventEntity e : events) {
                e.setId(id++);
              }
              return events;
            });
  }

  private static EventData data(String name, String start, String image, String category) {
    return new EventData(
        name,
        start,
        "2026-12-01T07:30:00.000Z",
        "Cubbon Park, Bengaluru",
        "https://site/e?utm_source=x&id=1",
        "Org",
        image,
        category,
        "desc",
        12.97,
        77.59,
        false,
        null,
        "https://site/list");
  }

  private static Item item(String id, EventData data) {
    return new Item(id, "2026-11-01T00:00:00Z", data);
  }

  private static Item item(String id) {
    return item(id, data("Event " + id, START, "https://img/" + id, "BEACH_CLEANUP"));
  }

  @SuppressWarnings("unchecked")
  private List<EventEntity> saved() {
    ArgumentCaptor<List<EventEntity>> captor = ArgumentCaptor.forClass(List.class);
    verify(eventService).saveAll(captor.capture());
    return captor.getValue();
  }

  @Test
  @DisplayName("Maps an item to an event: external_id, source_site_id, UTC times, link, image")
  void mapsAnItem() {
    int count = eventImportService.importItems("teameverest-ngo", List.of(item("itm_1")));

    assertThat(count).isEqualTo(1);
    EventEntity e = saved().get(0);
    assertThat(e.getName()).isEqualTo("Event itm_1");
    assertThat(e.getExternalId()).isEqualTo("itm_1");
    assertThat(e.getSourceSiteId()).isEqualTo("teameverest-ngo");
    assertThat(e.getPortal()).isEqualTo(EventPortalModel.TEAMEVEREST);
    assertThat(e.getType()).isEqualTo(EventTypeModel.BEACH_CLEANUP);
    assertThat(e.getStartTime()).isEqualTo(START_UTC);
    assertThat(e.getEndTime()).isEqualTo(LocalDateTime.of(2026, 12, 1, 7, 30));
    assertThat(e.getAddress()).isEqualTo("Cubbon Park, Bengaluru");
    assertThat(e.getLink()).isEqualTo("https://site/e?id=1");
    assertThat(e.getOrganisation()).isEqualTo("Org");
    assertThat(e.getMedia().getUrl()).isEqualTo("https://gcs/image.jpg");
    verify(eventImageService).downloadAndStore("https://img/itm_1");
  }

  @Test
  @DisplayName("Every imported event gets a PENDING approval row")
  @SuppressWarnings("unchecked")
  void createsPendingApprovals() {
    eventImportService.importItems("karmana-in", List.of(item("itm_1"), item("itm_2")));

    ArgumentCaptor<List<EventApprovalEntity>> captor = ArgumentCaptor.forClass(List.class);
    verify(eventApprovalRepository).saveAll(captor.capture());
    assertThat(captor.getValue())
        .hasSize(2)
        .allMatch(a -> a.getStatus() == EventApprovalStatus.PENDING && a.getEventId() != null);
  }

  @ParameterizedTest(name = "{0} → {1}")
  @CsvSource({
    "teameverest-ngo, TEAMEVEREST",
    "mybharat-gov-in, MYBHARATGOVIN",
    "ivolunteer-in, IVOLUNTEERIN",
    "karmana-in, WEB",
    "www-example-org, WEB"
  })
  @DisplayName("Portal is the known site's enum value, otherwise WEB")
  void mapsThePortal(String siteId, EventPortalModel expected) {
    eventImportService.importItems(siteId, List.of(item("itm_1")));

    EventEntity e = saved().get(0);
    assertThat(e.getPortal()).isEqualTo(expected);
    assertThat(e.getSourceSiteId()).isEqualTo(siteId);
  }

  @ParameterizedTest(name = "category {0} → {1}")
  @CsvSource(
      value = {
        "TREEPLANTATION, TREEPLANTATION",
        "beach_cleanup, BEACH_CLEANUP",
        "WORKSHOP, WORKSHOP",
        "SOMETHING_NEW, OTHER",
        "NULL, OTHER"
      },
      nullValues = "NULL")
  @DisplayName("Type is the category mapped to EventTypeModel, OTHER as fallback")
  void mapsTheCategory(String category, EventTypeModel expected) {
    eventImportService.importItems(
        "karmana-in", List.of(item("itm_1", data("E", START, "https://img", category))));

    assertThat(saved().get(0).getType()).isEqualTo(expected);
  }

  @Test
  @DisplayName("An item whose id is already in external_id is skipped")
  void dedupesOnExternalId() {
    when(eventRepository.existsByExternalId("itm_old")).thenReturn(true);

    int count =
        eventImportService.importItems("karmana-in", List.of(item("itm_old"), item("itm_new")));

    assertThat(count).isEqualTo(1);
    assertThat(saved()).extracting(EventEntity::getExternalId).containsExactly("itm_new");
    verify(eventImageService, never()).downloadAndStore("https://img/itm_old");
  }

  @Test
  @DisplayName("The same id twice in one read is imported once")
  void dedupesWithinTheBatch() {
    int count = eventImportService.importItems("karmana-in", List.of(item("itm_1"), item("itm_1")));

    assertThat(count).isEqualTo(1);
  }

  @Test
  @DisplayName("An older row with the same name + start time (no external_id) is not duplicated")
  void legacyNameAndStartDedupe() {
    when(eventRepository.existsByNameAndStartTime("Event itm_1", START_UTC)).thenReturn(true);

    int count = eventImportService.importItems("teameverest-ngo", List.of(item("itm_1")));

    assertThat(count).isZero();
    assertThat(saved()).isEmpty();
  }

  @Test
  @DisplayName("Items without an image, a name or a start time are skipped")
  void requiredFields() {
    int count =
        eventImportService.importItems(
            "karmana-in",
            List.of(
                item("no_image", data("A", START, null, "OTHER")),
                item("blank_image", data("B", START, " ", "OTHER")),
                item("no_name", data(" ", START, "https://img", "OTHER")),
                item("no_start", data("C", null, "https://img", "OTHER")),
                item("bad_start", data("D", "next week", "https://img", "OTHER")),
                new Item("no_data", null, null),
                item("ok", data("E", START, "https://img/ok", "OTHER"))));

    assertThat(count).isEqualTo(1);
    assertThat(saved()).extracting(EventEntity::getExternalId).containsExactly("ok");
    verify(eventImageService, never()).downloadAndStore(null);
  }

  @Test
  @DisplayName("An item whose image cannot be copied to GCS is skipped")
  void imageCopyFailureSkips() {
    when(eventImageService.downloadAndStore("https://img/itm_1")).thenReturn(null);

    int count = eventImportService.importItems("karmana-in", List.of(item("itm_1")));

    assertThat(count).isZero();
  }

  @Test
  @DisplayName("Online events without an address are stored as 'Online'; in-person ones skipped")
  void addressRules() {
    EventData online =
        new EventData(
            "Webinar",
            START,
            null,
            null,
            "https://l",
            null,
            "https://img",
            null,
            null,
            null,
            null,
            true,
            null,
            null);
    EventData inPerson =
        new EventData(
            "Drive",
            START,
            null,
            "",
            "https://l2",
            null,
            "https://img",
            null,
            null,
            null,
            null,
            false,
            null,
            null);

    eventImportService.importItems(
        "karmana-in", List.of(item("on", online), item("off", inPerson)));

    List<EventEntity> saved = saved();
    assertThat(saved).hasSize(1);
    assertThat(saved.get(0).getAddress()).isEqualTo("Online");
  }

  @Test
  @DisplayName("source_url is the link when the item has none")
  void sourceUrlFallback() {
    EventData noLink =
        new EventData(
            "E",
            START,
            null,
            "Addr",
            null,
            null,
            "https://img",
            null,
            null,
            null,
            null,
            false,
            null,
            "https://site/list");

    eventImportService.importItems("karmana-in", List.of(item("itm_1", noLink)));

    assertThat(saved().get(0).getLink()).isEqualTo("https://site/list");
  }

  @Test
  @DisplayName("Times with an offset are converted to UTC; times without one are kept")
  void parsesTimes() {
    assertThat(EventImportService.parseTime("2026-12-01T10:00:00+05:30")).isEqualTo(START_UTC);
    assertThat(EventImportService.parseTime("2026-12-01T04:30:00")).isEqualTo(START_UTC);
    assertThat(EventImportService.parseTime(START)).isEqualTo(START_UTC);
    assertThat(EventImportService.parseTime("garbage")).isNull();
    assertThat(EventImportService.parseTime(null)).isNull();
  }

  @Test
  @DisplayName("Returns 0 for an empty read without touching the database")
  void emptyRead() {
    assertThat(eventImportService.importItems("karmana-in", List.of())).isZero();
    assertThat(eventImportService.importItems("karmana-in", null)).isZero();
    verify(eventService, never()).saveAll(anyList());
  }
}
