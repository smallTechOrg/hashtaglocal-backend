package org.smalltech.hashtaglocal_backend.integration;

import static org.junit.jupiter.api.Assertions.*;

import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.smalltech.hashtaglocal_backend.dto.ScrapeEventDTO;
import org.smalltech.hashtaglocal_backend.dto.ScrapeResponseDTO;
import org.smalltech.hashtaglocal_backend.entity.EventEntity;
import org.smalltech.hashtaglocal_backend.model.EventPortalModel;
import org.smalltech.hashtaglocal_backend.model.EventTypeModel;
import org.smalltech.hashtaglocal_backend.repository.EventRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Mono;

/**
 * Integration tests for {@code POST /api/v1/events/import}.
 *
 * <p>Posts a scrape service JSON payload and verifies events are persisted correctly in the
 * database.
 *
 * <p>Key behaviours verified:
 *
 * <ul>
 *   <li>All valid events in the payload become EventEntity rows in the DB
 *   <li>location_id is null immediately after import — geocoding is a separate step
 *   <li>Duplicate events (same name + startTime) are not re-inserted on a second call
 * </ul>
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@DisplayName("POST /api/v1/events/import")
class EventImportIntegrationTest {

  @Autowired private WebTestClient webTestClient;
  @Autowired private EventRepository eventRepository;

  private static final LocalDateTime START_TIME = LocalDateTime.of(2026, 2, 21, 5, 0);

  @BeforeEach
  void cleanUp() {
    eventRepository.deleteAll();
  }

  private ScrapeResponseDTO payload(ScrapeEventDTO... events) {
    return ScrapeResponseDTO.builder()
        .data(ScrapeResponseDTO.Data.builder().events(List.of(events)).build())
        .build();
  }

  @Test
  @DisplayName("Imports all events from the payload and persists them to the database")
  void importsPayloadAndPersistsEvents() {
    ScrapeEventDTO trek =
        ScrapeEventDTO.builder()
            .name("Trek and Plog")
            .organisation("Team Everest")
            .portal("Team everest")
            .type("TREKANDPLOG")
            .startTime(START_TIME)
            .address("Lalbagh Main gate, Bengaluru")
            .link("https://www.teameverest.ngo/events/trek-plog-bengaluru1")
            .image("https://images.unsplash.com/photo-1551632811.jpg")
            .build();

    ScrapeEventDTO beach =
        ScrapeEventDTO.builder()
            .name("Beach Cleanup")
            .organisation("ivolunteer")
            .portal("ivolunteer")
            .type("BEACH_CLEANUP")
            .startTime(LocalDateTime.of(2026, 2, 21, 0, 0))
            .address("Holy Cross Church, Vile Parle West, Mumbai")
            .link("https://www.ivolunteer.in/opportunity/beach-cleanup")
            .build();

    webTestClient
        .post()
        .uri("/api/v1/events/import")
        .contentType(MediaType.APPLICATION_JSON)
        .body(Mono.just(payload(trek, beach)), ScrapeResponseDTO.class)
        .exchange()
        .expectStatus()
        .isOk()
        .expectBody(String.class)
        .value(body -> assertTrue(body.contains("2"), "Response should confirm 2 events imported"));

    List<EventEntity> events = eventRepository.findAll();
    assertEquals(2, events.size());

    EventEntity trekEntity =
        events.stream()
            .filter(e -> "Trek and Plog".equals(e.getEventName()))
            .findFirst()
            .orElseThrow(() -> new AssertionError("Trek and Plog event not found in DB"));

    assertEquals("Team Everest", trekEntity.getOrganisation());
    assertEquals(EventPortalModel.TEAMEVEREST, trekEntity.getPortal());
    assertEquals(EventTypeModel.TREKANDPLOG, trekEntity.getEventType());
    assertEquals("Lalbagh Main gate, Bengaluru", trekEntity.getAddress());
    assertNull(trekEntity.getLocation(), "location_id should be null until geocoded");
    assertNull(trekEntity.getMetaData());
  }

  @Test
  @DisplayName("Duplicate events are not re-inserted on a second import call")
  void deduplicatesOnSecondImport() {
    ScrapeEventDTO trek =
        ScrapeEventDTO.builder()
            .name("Trek and Plog")
            .organisation("Team Everest")
            .portal("Team everest")
            .type("TREKANDPLOG")
            .startTime(START_TIME)
            .address("Lalbagh Main gate, Bengaluru")
            .link("https://www.teameverest.ngo/events/trek-plog-bengaluru1")
            .build();

    // First import — should persist 1 event
    webTestClient
        .post()
        .uri("/api/v1/events/import")
        .contentType(MediaType.APPLICATION_JSON)
        .body(Mono.just(payload(trek)), ScrapeResponseDTO.class)
        .exchange()
        .expectStatus()
        .isOk();

    assertEquals(1, eventRepository.count());

    // Second import with same event — duplicate must be skipped
    webTestClient
        .post()
        .uri("/api/v1/events/import")
        .contentType(MediaType.APPLICATION_JSON)
        .body(Mono.just(payload(trek)), ScrapeResponseDTO.class)
        .exchange()
        .expectStatus()
        .isOk()
        .expectBody(String.class)
        .value(body -> assertTrue(body.contains("0"), "Second import should save 0 new events"));

    assertEquals(1, eventRepository.count(), "DB must still have exactly 1 event after dedup");
  }

  @Test
  @DisplayName("Returns 0 count for a payload with an empty events list")
  void returnsZeroForEmptyEventsList() {
    ScrapeResponseDTO emptyPayload =
        ScrapeResponseDTO.builder()
            .data(ScrapeResponseDTO.Data.builder().events(List.of()).build())
            .build();

    webTestClient
        .post()
        .uri("/api/v1/events/import")
        .contentType(MediaType.APPLICATION_JSON)
        .body(Mono.just(emptyPayload), ScrapeResponseDTO.class)
        .exchange()
        .expectStatus()
        .isOk()
        .expectBody(String.class)
        .value(body -> assertTrue(body.contains("0")));

    assertEquals(0, eventRepository.count());
  }
}
