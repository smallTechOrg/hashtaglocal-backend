package org.smalltech.hashtaglocal_backend.integration;

import static org.junit.jupiter.api.Assertions.*;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.io.IOException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.smalltech.hashtaglocal_backend.dto.ScrapeEventDTO;
import org.smalltech.hashtaglocal_backend.model.EventPortalModel;
import org.smalltech.hashtaglocal_backend.model.EventTypeModel;
import org.smalltech.hashtaglocal_backend.repository.EventRepository;
import org.smalltech.hashtaglocal_backend.service.EventImportService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

/**
 * Integration tests for {@link EventImportService}.
 *
 * <p>Calls {@code importFromScrapeResponse()} directly against a real test database to verify:
 *
 * <ul>
 *   <li>Valid events are persisted with correct field mappings
 *   <li>{@code location_id} is {@code null} immediately after import — geocoding is separate
 *   <li>Duplicate events (same name + startTime) are not re-inserted
 *   <li>Events with blank name or null startTime are silently skipped
 * </ul>
 *
 * <p>Test scenarios are defined in {@code event-import-integration-test-cases.json}.
 */
@SpringBootTest
@ActiveProfiles("test")
@DisplayName("EventImportService — integration")
class EventImportIntegrationTest {

  @Autowired private EventImportService eventImportService;
  @Autowired private EventRepository eventRepository;

  private static final ObjectMapper MAPPER =
      new ObjectMapper()
          .registerModule(new JavaTimeModule())
          .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

  @BeforeEach
  void cleanUp() {
    eventRepository.deleteAll();
  }

  // ---------------------------------------------------------------------------
  // Records matching event-import-integration-test-cases.json
  // ---------------------------------------------------------------------------

  record EventInput(
      String name,
      String organisation,
      String portal,
      String type,
      @JsonProperty("start_time") LocalDateTime startTime,
      String address,
      String link,
      String image) {}

  record ImportTestCase(
      String scenario,
      String description,
      List<EventInput> events,
      @JsonProperty("import_twice") boolean importTwice,
      @JsonProperty("expected_count") int expectedCount) {}

  // ---------------------------------------------------------------------------
  // Parameterised: all non-dedup scenarios from JSON
  // ---------------------------------------------------------------------------

  static Stream<Arguments> importScenarios() throws IOException {
    List<ImportTestCase> cases =
        MAPPER.readValue(
            EventImportIntegrationTest.class.getResourceAsStream(
                "/event-import-integration-test-cases.json"),
            new TypeReference<>() {});
    return cases.stream()
        .filter(tc -> !tc.importTwice())
        .map(tc -> Arguments.of(tc.scenario(), tc.description(), tc.events(), tc.expectedCount()));
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("importScenarios")
  @DisplayName("Import scenario")
  void importScenario(
      String scenario, String description, List<EventInput> inputs, int expectedCount) {
    int saved = eventImportService.importFromScrapeResponse(toDtos(inputs));

    assertEquals(expectedCount, saved, description);
    assertEquals(expectedCount, eventRepository.count(), description);
  }

  // ---------------------------------------------------------------------------
  // Field mapping
  // ---------------------------------------------------------------------------

  @Test
  @DisplayName("Imports event and maps all fields correctly — location_id is null until geocoded")
  void importsEventAndMapsFieldsCorrectly() {
    ScrapeEventDTO trek =
        ScrapeEventDTO.builder()
            .name("Trek and Plog")
            .organisation("Team Everest")
            .portal("Team everest")
            .type("TREKANDPLOG")
            .startTime(LocalDateTime.of(2026, 2, 21, 5, 0))
            .address("Lalbagh Main gate, Bengaluru")
            .link("https://www.teameverest.ngo/events/trek-plog-bengaluru1")
            .image("https://images.unsplash.com/photo-1551632811.jpg")
            .build();

    eventImportService.importFromScrapeResponse(List.of(trek));

    var saved =
        eventRepository.findAll().stream()
            .filter(e -> "Trek and Plog".equals(e.getEventName()))
            .findFirst()
            .orElseThrow();

    assertEquals("Team Everest", saved.getOrganisation());
    assertEquals(EventPortalModel.TEAMEVEREST, saved.getPortal());
    assertEquals(EventTypeModel.TREKANDPLOG, saved.getEventType());
    assertEquals("Lalbagh Main gate, Bengaluru", saved.getAddress());
    assertNull(saved.getLocation(), "location_id must be null until geocoding runs");
  }

  // ---------------------------------------------------------------------------
  // Parameterised: dedup scenarios (import_twice: true) — loaded from JSON
  // ---------------------------------------------------------------------------

  static Stream<Arguments> dedupScenarios() throws IOException {
    List<ImportTestCase> cases =
        MAPPER.readValue(
            EventImportIntegrationTest.class.getResourceAsStream(
                "/event-import-integration-test-cases.json"),
            new TypeReference<>() {});
    return cases.stream()
        .filter(ImportTestCase::importTwice)
        .map(tc -> Arguments.of(tc.scenario(), tc.description(), tc.events(), tc.expectedCount()));
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("dedupScenarios")
  @DisplayName("Dedup scenario — already-imported event is not re-inserted on second import call")
  void dedupScenario(
      String scenario, String description, List<EventInput> inputs, int expectedCount) {
    List<ScrapeEventDTO> dtos = toDtos(inputs);

    // First import — seeds the DB
    eventImportService.importFromScrapeResponse(dtos);
    assertEquals(expectedCount, eventRepository.count(), "First import: " + description);

    // Second import — same events, must all be recognised as duplicates
    int secondRun = eventImportService.importFromScrapeResponse(dtos);

    assertEquals(0, secondRun, "Second import must save 0 new rows: " + description);
    assertEquals(
        expectedCount, eventRepository.count(), "DB count must not change: " + description);
  }

  // ---------------------------------------------------------------------------
  // Helper
  // ---------------------------------------------------------------------------

  private List<ScrapeEventDTO> toDtos(List<EventInput> inputs) {
    return inputs.stream()
        .map(
            e ->
                ScrapeEventDTO.builder()
                    .name(e.name())
                    .organisation(e.organisation())
                    .portal(e.portal())
                    .type(e.type())
                    .startTime(e.startTime())
                    .address(e.address())
                    .link(e.link())
                    .image(e.image())
                    .build())
        .toList();
  }
}
