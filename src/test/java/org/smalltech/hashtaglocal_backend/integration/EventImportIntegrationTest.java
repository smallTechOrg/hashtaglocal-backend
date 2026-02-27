package org.smalltech.hashtaglocal_backend.integration;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.smalltech.hashtaglocal_backend.entity.EventEntity;
import org.smalltech.hashtaglocal_backend.repository.EventRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.client.MultipartBodyBuilder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.springframework.web.reactive.function.BodyInserters;

/**
 * Integration tests for {@code POST /admin/events/import}.
 *
 * <p>Uploads a real CSV file and verifies the events are persisted correctly to the database. The
 * test CSV lives at {@code src/test/resources/test-events.csv}.
 *
 * <p>Key behaviours verified:
 *
 * <ul>
 *   <li>All valid CSV rows become EventEntity rows in the DB
 *   <li>location_id is null immediately after import — geocoding is a separate step
 *   <li>The city column is stored in meta_data, not a dedicated column
 * </ul>
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@DisplayName("POST /admin/events/import")
class EventImportIntegrationTest {

  @Autowired private WebTestClient webTestClient;
  @Autowired private EventRepository eventRepository;

  @BeforeEach
  void cleanUp() {
    eventRepository.deleteAll();
  }

  @Test
  @DisplayName("Imports all valid rows from the CSV and persists them to the database")
  void importsCsvAndPersistsEvents() {
    MultipartBodyBuilder builder = new MultipartBodyBuilder();
    builder.part("file", new ClassPathResource("test-events.csv")).filename("test-events.csv");

    webTestClient
        .post()
        .uri("/admin/events/import")
        .body(BodyInserters.fromMultipartData(builder.build()))
        .exchange()
        .expectStatus()
        .isOk()
        .expectBody(String.class)
        .value(body -> assertTrue(body.contains("2"), "Response should confirm 2 events imported"));

    List<EventEntity> events = eventRepository.findAll();
    assertEquals(2, events.size());

    EventEntity treePlantation =
        events.stream()
            .filter(e -> "Tree Plantation Drive".equals(e.getEventName()))
            .findFirst()
            .orElseThrow(() -> new AssertionError("Tree Plantation Drive event not found in DB"));

    assertEquals("Green India", treePlantation.getOrganisation());
    assertEquals("mybharat.gov.in", treePlantation.getPlatform());
    assertEquals("Lalbagh Main gate", treePlantation.getAddress());

    // location is null — geocoding has not been run yet
    assertNull(treePlantation.getLocation(), "location_id should be null until geocoded");

    // city is not a DB column — it lives in meta_data
    assertNotNull(treePlantation.getMetaData());
    assertEquals("Bengaluru", treePlantation.getMetaData().get("city"));
  }

  @Test
  @DisplayName("Returns a 0 count response for a CSV that contains only the header row")
  void returnsZeroForHeaderOnlyCsv() {
    byte[] headerOnly =
        "event_name,city,organisation,platform,event_type,start_time,end_time,address,link\n"
            .getBytes();

    MultipartBodyBuilder builder = new MultipartBodyBuilder();
    builder
        .part("file", headerOnly)
        .header("Content-Disposition", "form-data; name=\"file\"; filename=\"empty.csv\"")
        .header("Content-Type", "text/csv");

    webTestClient
        .post()
        .uri("/admin/events/import")
        .body(BodyInserters.fromMultipartData(builder.build()))
        .exchange()
        .expectStatus()
        .isOk()
        .expectBody(String.class)
        .value(body -> assertTrue(body.contains("0")));

    assertEquals(0, eventRepository.count());
  }
}
