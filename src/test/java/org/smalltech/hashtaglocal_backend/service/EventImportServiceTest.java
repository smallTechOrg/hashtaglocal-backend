package org.smalltech.hashtaglocal_backend.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.smalltech.hashtaglocal_backend.dto.ScrapeEventDTO;
import org.smalltech.hashtaglocal_backend.entity.EventEntity;
import org.smalltech.hashtaglocal_backend.model.EventPortalModel;
import org.smalltech.hashtaglocal_backend.model.EventTypeModel;
import org.smalltech.hashtaglocal_backend.repository.EventRepository;

/**
 * Unit tests for {@link EventImportService} JSON import logic.
 *
 * <p>Parameterised type-mapping and portal-mapping tests load their inputs from {@code
 * event-import-service-test-cases.json} so new cases can be added without touching Java code.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("EventImportService — JSON import")
class EventImportServiceTest {

  @Mock private EventService eventService;
  @Mock private EventRepository eventRepository;

  @InjectMocks private EventImportService eventImportService;

  private static final LocalDateTime START_TIME = LocalDateTime.of(2026, 2, 21, 5, 0);

  private static final ObjectMapper MAPPER = new ObjectMapper();

  // ---------------------------------------------------------------------------
  // Helpers
  // ---------------------------------------------------------------------------

  private ScrapeEventDTO dto(String name, String type, String portal, LocalDateTime start) {
    return ScrapeEventDTO.builder()
        .name(name)
        .organisation("Test Org")
        .type(type)
        .portal(portal)
        .startTime(start)
        .address("Lalbagh Main Gate, Bengaluru")
        .link("https://example.com")
        .image("https://example.com/image.jpg")
        .build();
  }

  @SuppressWarnings("unchecked")
  private List<EventEntity> capturedSavedEvents() {
    ArgumentCaptor<List<EventEntity>> captor = ArgumentCaptor.forClass(List.class);
    verify(eventService).saveAll(captor.capture());
    return captor.getValue();
  }

  // ---------------------------------------------------------------------------
  // Parameterised: type mapping — loaded from event-import-service-test-cases.json
  // ---------------------------------------------------------------------------

  static Stream<Arguments> typeMappingCases() throws IOException {
    JsonNode root =
        MAPPER.readTree(
            EventImportServiceTest.class.getResourceAsStream(
                "/event-import-service-test-cases.json"));
    return Stream.iterate(0, i -> i + 1)
        .limit(root.get("type_mapping").size())
        .map(
            i -> {
              JsonNode node = root.get("type_mapping").get(i);
              String raw = node.get("raw").isNull() ? null : node.get("raw").asText();
              String expected = node.get("expected").asText();
              String description = node.get("description").asText();
              return Arguments.of(raw, expected, description);
            });
  }

  @ParameterizedTest(name = "{2}")
  @MethodSource("typeMappingCases")
  @DisplayName("Type string correctly maps to EventTypeModel")
  void mapsTypeStringToEventTypeModel(String raw, String expected, String description) {
    when(eventRepository.existsByNameAndStartTime(any(), any())).thenReturn(false);

    eventImportService.importFromScrapeResponse(
        List.of(dto("Test Event", raw, "Team everest", START_TIME)));

    assertEquals(EventTypeModel.valueOf(expected), capturedSavedEvents().get(0).getType());
  }

  // ---------------------------------------------------------------------------
  // Parameterised: portal mapping — loaded from event-import-service-test-cases.json
  // ---------------------------------------------------------------------------

  static Stream<Arguments> portalMappingCases() throws IOException {
    JsonNode root =
        MAPPER.readTree(
            EventImportServiceTest.class.getResourceAsStream(
                "/event-import-service-test-cases.json"));
    return Stream.iterate(0, i -> i + 1)
        .limit(root.get("portal_mapping").size())
        .map(
            i -> {
              JsonNode node = root.get("portal_mapping").get(i);
              String raw = node.get("raw").isNull() ? null : node.get("raw").asText();
              String expected =
                  node.get("expected").isNull() ? null : node.get("expected").asText();
              String description = node.get("description").asText();
              return Arguments.of(raw, expected, description);
            });
  }

  @ParameterizedTest(name = "{2}")
  @MethodSource("portalMappingCases")
  @DisplayName("Portal string correctly maps to EventPortalModel")
  void mapsPortalStringToEventPortalModel(String raw, String expected, String description) {
    when(eventRepository.existsByNameAndStartTime(any(), any())).thenReturn(false);

    eventImportService.importFromScrapeResponse(
        List.of(dto("Test Event", "TREKANDPLOG", raw, START_TIME)));

    EventPortalModel expectedPortal = expected != null ? EventPortalModel.valueOf(expected) : null;
    assertEquals(expectedPortal, capturedSavedEvents().get(0).getPortal());
  }

  // ---------------------------------------------------------------------------
  // Field mapping
  // ---------------------------------------------------------------------------

  @Test
  @DisplayName("Maps all DTO fields to the correct EventEntity fields")
  void mapsAllFieldsCorrectly() {
    when(eventRepository.existsByNameAndStartTime(any(), any())).thenReturn(false);

    eventImportService.importFromScrapeResponse(
        List.of(dto("Trek and Plog", "TREKANDPLOG", "Team everest", START_TIME)));

    EventEntity saved = capturedSavedEvents().get(0);
    assertEquals("Trek and Plog", saved.getName());
    assertEquals("Test Org", saved.getOrganisation());
    assertEquals(EventTypeModel.TREKANDPLOG, saved.getType());
    assertEquals(EventPortalModel.TEAMEVEREST, saved.getPortal());
    assertEquals(START_TIME, saved.getStartTime());
    assertEquals("Lalbagh Main Gate, Bengaluru", saved.getAddress());
    assertEquals("https://example.com", saved.getLink());
    assertEquals("https://example.com/image.jpg", saved.getImageUrl());
    assertNull(saved.getLocation(), "location_id should be null until geocoding runs");
  }

  @Test
  @DisplayName("Null endTime is accepted and stored as null")
  void nullEndTimeIsAccepted() {
    when(eventRepository.existsByNameAndStartTime(any(), any())).thenReturn(false);
    ScrapeEventDTO noEnd =
        ScrapeEventDTO.builder()
            .name("Trek and Plog")
            .organisation("Org")
            .portal("Team everest")
            .type("TREKANDPLOG")
            .startTime(START_TIME)
            .endTime(null)
            .address("Lalbagh Main Gate, Bengaluru")
            .link("https://example.com")
            .image("https://example.com/image.jpg")
            .build();

    eventImportService.importFromScrapeResponse(List.of(noEnd));

    assertNull(capturedSavedEvents().get(0).getEndTime());
  }

  // ---------------------------------------------------------------------------
  // Deduplication
  // ---------------------------------------------------------------------------

  @Test
  @DisplayName("Duplicate event (same name + startTime) is skipped — count = 0")
  void skipsDuplicateEvent() {
    when(eventRepository.existsByNameAndStartTime("Trek and Plog", START_TIME)).thenReturn(true);

    int count =
        eventImportService.importFromScrapeResponse(
            List.of(dto("Trek and Plog", "TREKANDPLOG", "Team everest", START_TIME)));

    assertEquals(0, count);
    verify(eventService).saveAll(List.of());
  }

  @Test
  @DisplayName("Only new events are saved when duplicates are mixed in")
  void savesOnlyNewEventsWhenMixedWithDuplicates() {
    LocalDateTime t1 = LocalDateTime.of(2026, 2, 21, 5, 0);
    LocalDateTime t2 = LocalDateTime.of(2026, 3, 7, 0, 0);

    when(eventRepository.existsByNameAndStartTime("Trek and Plog", t1)).thenReturn(true);
    when(eventRepository.existsByNameAndStartTime("Green Touch", t2)).thenReturn(false);

    int count =
        eventImportService.importFromScrapeResponse(
            List.of(
                dto("Trek and Plog", "TREKANDPLOG", "Team everest", t1),
                dto("Green Touch", "FOREST_CLEANUP", "ivolunteer", t2)));

    assertEquals(1, count);
    assertEquals("Green Touch", capturedSavedEvents().get(0).getName());
  }

  // ---------------------------------------------------------------------------
  // Skip / edge cases
  // ---------------------------------------------------------------------------

  @Test
  @DisplayName("Event with blank name is silently skipped")
  void skipsEventWithBlankName() {
    ScrapeEventDTO blankName =
        ScrapeEventDTO.builder().name("").organisation("Org").startTime(START_TIME).build();

    int count = eventImportService.importFromScrapeResponse(List.of(blankName));

    assertEquals(0, count);
    verify(eventRepository, never()).existsByNameAndStartTime(any(), any());
  }

  @Test
  @DisplayName("Event with null startTime is silently skipped")
  void skipsEventWithNullStartTime() {
    ScrapeEventDTO noStart =
        ScrapeEventDTO.builder().name("Some Event").organisation("Org").startTime(null).build();

    int count = eventImportService.importFromScrapeResponse(List.of(noStart));

    assertEquals(0, count);
    verify(eventRepository, never()).existsByNameAndStartTime(any(), any());
  }

  @Test
  @DisplayName("Returns 0 immediately for null or empty list")
  void returnsZeroForNullOrEmptyList() {
    assertEquals(0, eventImportService.importFromScrapeResponse(null));
    assertEquals(0, eventImportService.importFromScrapeResponse(List.of()));
    verifyNoInteractions(eventRepository);
  }
}
