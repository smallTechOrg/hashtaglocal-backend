package org.smalltech.hashtaglocal_backend.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.smalltech.hashtaglocal_backend.entity.EventEntity;
import org.smalltech.hashtaglocal_backend.model.EventTypeModel;
import org.springframework.mock.web.MockMultipartFile;

/**
 * Unit tests for {@link EventImportService} CSV parsing logic.
 *
 * <p>CSV column order: event_name | city | organisation | platform | event_type | start_time |
 * end_time | address | link
 *
 * <p>These tests cover the parsing and mapping rules — not HTTP or the database.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("EventImportService — CSV parsing")
class EventImportServiceTest {

  @Mock private EventService eventService;

  @InjectMocks private EventImportService eventImportService;

  private static final String HEADER =
      "event_name,city,organisation,platform,event_type,start_time,end_time,address,link\n";

  private MockMultipartFile csv(String rows) {
    return new MockMultipartFile(
        "file", "events.csv", "text/csv", (HEADER + rows).getBytes(StandardCharsets.UTF_8));
  }

  @SuppressWarnings("unchecked")
  private List<EventEntity> capturedSavedEvents() {
    ArgumentCaptor<List<EventEntity>> captor = ArgumentCaptor.forClass(List.class);
    verify(eventService).saveAll(captor.capture());
    return captor.getValue();
  }

  @Test
  @DisplayName("Parses all columns from a complete row into the correct EventEntity fields")
  void parsesCompleteRow() throws Exception {
    eventImportService.importFromCsv(
        csv(
            "Tree Plantation Drive,Bengaluru,Green India,mybharat.gov.in,"
                + "tree plantation,February 21,February 21,Lalbagh Main gate,"
                + "https://example.com/1\n"));

    EventEntity event = capturedSavedEvents().get(0);
    assertEquals("Tree Plantation Drive", event.getEventName());
    assertEquals("Green India", event.getOrganisation());
    assertEquals("mybharat.gov.in", event.getPlatform());
    assertEquals(EventTypeModel.TREEPLANTATION, event.getEventType());
    assertEquals("Lalbagh Main gate", event.getAddress());
    assertEquals("https://example.com/1", event.getLink());
  }

  @Test
  @DisplayName("Parses 'Month Day' date string to midnight of the current year")
  void parsesMonthDayDate() throws Exception {
    eventImportService.importFromCsv(csv("Event,City,Org,,,February 21,March 1,,\n"));

    EventEntity event = capturedSavedEvents().get(0);
    int currentYear = LocalDate.now().getYear();
    assertNotNull(event.getStartTime());
    assertEquals(currentYear, event.getStartTime().getYear());
    assertEquals(2, event.getStartTime().getMonthValue());
    assertEquals(21, event.getStartTime().getDayOfMonth());
    assertEquals(0, event.getStartTime().getHour()); // stored at midnight
  }

  @Test
  @DisplayName("Parses 'dd/MM/yyyy HH:mm:ss' date string preserving the exact time")
  void parsesFullDatetime() throws Exception {
    eventImportService.importFromCsv(
        csv("Event,City,Org,,,21/02/2026 05:00:00,22/02/2026 18:30:00,,\n"));

    EventEntity event = capturedSavedEvents().get(0);
    assertNotNull(event.getStartTime());
    assertEquals(2026, event.getStartTime().getYear());
    assertEquals(2, event.getStartTime().getMonthValue());
    assertEquals(21, event.getStartTime().getDayOfMonth());
    assertEquals(5, event.getStartTime().getHour());
    assertNotNull(event.getEndTime());
    assertEquals(18, event.getEndTime().getHour());
    assertEquals(30, event.getEndTime().getMinute());
  }

  @Test
  @DisplayName("Maps unrecognised event type string to OTHER")
  void mapsUnknownEventTypeToOther() throws Exception {
    eventImportService.importFromCsv(csv("Event,City,Org,,random type,,,,\n"));

    EventEntity event = capturedSavedEvents().get(0);
    assertEquals(EventTypeModel.OTHER, event.getEventType());
  }

  @Test
  @DisplayName("Skips rows where event_name is blank — they are silently dropped")
  void skipsRowWithBlankEventName() throws Exception {
    int count = eventImportService.importFromCsv(csv(",City,Org,,,,,,\n"));

    assertEquals(0, count);
    verify(eventService).saveAll(List.of());
  }

  @Test
  @DisplayName("Returns count equal to valid rows — blank-name rows do not count")
  void returnsCountOfValidRowsOnly() throws Exception {
    int count =
        eventImportService.importFromCsv(
            csv(
                "Event 1,City,Org A,,,,,,\n"
                    + "Event 2,City,Org B,,,,,,\n"
                    + ",City,Org C,,,,,,\n")); // blank name — skipped

    assertEquals(2, count);
  }
}
