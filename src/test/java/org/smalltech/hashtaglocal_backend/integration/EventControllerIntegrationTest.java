package org.smalltech.hashtaglocal_backend.integration;

import java.time.LocalDateTime;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
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
 * <p>Expected response shape:
 *
 * <pre>
 * {
 *   "data": {
 *     "events": [
 *       {
 *         "id": 1,
 *         "name": "Tree Plantation Drive",
 *         "organisation": "Green India",
 *         "image_url": null,
 *         "portal": "MYBHARATGOVIN",
 *         "type": "TREEPLANTATION",
 *         "start_time": "2026-02-21T00:00:00",
 *         "end_time": null,
 *         "location": null,
 *         "address": "Lalbagh Main gate, Bengaluru",
 *         "link": null,
 *         "meta_data": { "city": "Bengaluru" }
 *       }
 *     ]
 *   }
 * }
 * </pre>
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@DisplayName("GET /api/v1/events")
class EventControllerIntegrationTest {

  @Autowired private WebTestClient webTestClient;
  @Autowired private EventRepository eventRepository;

  @BeforeEach
  void cleanUp() {
    eventRepository.deleteAll();
  }

  @Test
  @DisplayName("Returns an empty events array when no events exist in the database")
  void returnsEmptyListWhenNoEventsExist() {
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
        .isEqualTo(0);
  }

  @Test
  @DisplayName("Events without a geocoded location are excluded from the response")
  void eventsWithoutLocationAreExcluded() {
    eventRepository.save(
        EventEntity.builder()
            .eventName("Tree Plantation Drive")
            .organisation("Green India")
            .portal(EventPortalModel.MYBHARATGOVIN)
            .eventType(EventTypeModel.TREEPLANTATION)
            .startTime(LocalDateTime.of(2026, 2, 21, 0, 0))
            .address("Lalbagh Main gate, Bengaluru")
            .link("https://example.com/event/1")
            .metaData(Map.of("city", "Bengaluru"))
            .build());

    webTestClient
        .get()
        .uri("/api/v1/events")
        .exchange()
        .expectStatus()
        .isOk()
        .expectBody()
        .jsonPath("$.data.events.length()")
        .isEqualTo(0);
  }

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
        .isArray()
        .jsonPath("$.data.events.length()")
        .isEqualTo(0);
  }
}
