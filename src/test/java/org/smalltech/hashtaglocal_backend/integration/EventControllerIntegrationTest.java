package org.smalltech.hashtaglocal_backend.integration;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
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
import org.smalltech.hashtaglocal_backend.entity.EventEntity;
import org.smalltech.hashtaglocal_backend.model.EventPortalModel;
import org.smalltech.hashtaglocal_backend.model.EventTypeModel;
import org.smalltech.hashtaglocal_backend.repository.EventRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.reactive.server.WebTestClient;

/**
 * Integration tests for {@code GET /api/v1/events}.
 *
 * <p>Scenarios are defined in {@code event-controller-test-cases.json}. Each scenario seeds a
 * number of un-geocoded events (location_id = null) and asserts the expected API response count.
 * Un-geocoded events must never appear in the response.
 *
 * <p>Expected response shape:
 *
 * <pre>
 * { "data": { "events": [ ... ] } }
 * </pre>
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@DisplayName("GET /api/v1/events")
class EventControllerIntegrationTest {

  @Autowired private WebTestClient webTestClient;
  @Autowired private EventRepository eventRepository;

  private static final ObjectMapper MAPPER =
      new ObjectMapper().configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

  @BeforeEach
  void cleanUp() {
    eventRepository.deleteAll();
  }

  // ---------------------------------------------------------------------------
  // Record matching event-controller-test-cases.json
  // ---------------------------------------------------------------------------

  record ControllerTestCase(
      String scenario,
      String description,
      @JsonProperty("seed_events_without_location") int seedEventsWithoutLocation,
      @JsonProperty("expected_event_count") int expectedEventCount) {}

  // ---------------------------------------------------------------------------
  // Parameterised: seed N un-geocoded events → expect 0 from the API
  // ---------------------------------------------------------------------------

  static Stream<Arguments> controllerScenarios() throws IOException {
    List<ControllerTestCase> cases =
        MAPPER.readValue(
            EventControllerIntegrationTest.class.getResourceAsStream(
                "/event-controller-test-cases.json"),
            new TypeReference<>() {});
    return cases.stream()
        .map(
            tc ->
                Arguments.of(
                    tc.scenario(),
                    tc.description(),
                    tc.seedEventsWithoutLocation(),
                    tc.expectedEventCount()));
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("controllerScenarios")
  @DisplayName("Controller scenario")
  void controllerScenario(String scenario, String description, int seedCount, int expectedCount) {

    for (int i = 0; i < seedCount; i++) {
      eventRepository.save(ungeocodedEvent("Seeded Event " + i, i));
    }

    webTestClient
        .get()
        .uri("/api/v1/events")
        .exchange()
        .expectStatus()
        .isOk()
        .expectBody()
        .jsonPath("$.data.events")
        .isArray()
        .jsonPath("$.data.events.length()")
        .isEqualTo(expectedCount);
  }

  // ---------------------------------------------------------------------------
  // Response shape assertion — always has data.events array
  // ---------------------------------------------------------------------------

  @Test
  @DisplayName("Response wrapper shape is correct — data.events array is always present")
  void returnsCorrectResponseShape() {
    webTestClient
        .get()
        .uri("/api/v1/events")
        .exchange()
        .expectStatus()
        .isOk()
        .expectBody()
        .jsonPath("$.data")
        .exists()
        .jsonPath("$.data.events")
        .isArray();
  }

  // ---------------------------------------------------------------------------
  // Helper
  // ---------------------------------------------------------------------------

  private EventEntity ungeocodedEvent(String name, int index) {
    return EventEntity.builder()
        .name(name)
        .organisation("Test Org")
        .portal(EventPortalModel.TEAMEVEREST)
        .type(EventTypeModel.TREKANDPLOG)
        .startTime(LocalDateTime.of(2026, 2, 21 + index, 0, 0))
        .address("Lalbagh Main gate, Bengaluru")
        .link("https://example.com/event/" + index)
        .build();
  }
}
