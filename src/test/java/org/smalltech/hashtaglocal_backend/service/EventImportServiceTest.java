package org.smalltech.hashtaglocal_backend.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
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
 * <p>These tests cover mapping, deduplication, and edge-case behaviour — not HTTP or the database.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("EventImportService — JSON import")
class EventImportServiceTest {

  @Mock private EventService eventService;
  @Mock private EventRepository eventRepository;

  @InjectMocks private EventImportService eventImportService;

  private static final LocalDateTime START_TIME = LocalDateTime.of(2026, 2, 21, 5, 0);

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

  @Test
  @DisplayName("Maps all DTO fields to the correct EventEntity fields")
  void mapsAllFieldsCorrectly() {
    when(eventRepository.existsByEventNameAndStartTime(any(), any())).thenReturn(false);

    eventImportService.importFromScrapeResponse(
        List.of(dto("Trek and Plog", "TREKANDPLOG", "Team everest", START_TIME)));

    EventEntity saved = capturedSavedEvents().get(0);
    assertEquals("Trek and Plog", saved.getEventName());
    assertEquals("Test Org", saved.getOrganisation());
    assertEquals(EventTypeModel.TREKANDPLOG, saved.getEventType());
    assertEquals(EventPortalModel.TEAMEVEREST, saved.getPortal());
    assertEquals(START_TIME, saved.getStartTime());
    assertEquals("Lalbagh Main Gate, Bengaluru", saved.getAddress());
    assertEquals("https://example.com", saved.getLink());
    assertEquals("https://example.com/image.jpg", saved.getImageUrl());
    assertNull(saved.getLocation(), "location_id should be null until geocoding runs");
  }

  @Test
  @DisplayName("Maps 'BEACH_CLEANUP' type string to EventTypeModel.BEACH_CLEANUP")
  void mapsBeachCleanupType() {
    when(eventRepository.existsByEventNameAndStartTime(any(), any())).thenReturn(false);

    eventImportService.importFromScrapeResponse(
        List.of(dto("Beach Cleanup", "BEACH_CLEANUP", "ivolunteer", START_TIME)));

    assertEquals(EventTypeModel.BEACH_CLEANUP, capturedSavedEvents().get(0).getEventType());
  }

  @Test
  @DisplayName("Unknown type string falls back to EventTypeModel.OTHER")
  void unknownTypeFallsBackToOther() {
    when(eventRepository.existsByEventNameAndStartTime(any(), any())).thenReturn(false);

    eventImportService.importFromScrapeResponse(
        List.of(dto("Some Event", "UNKNOWN_ACTIVITY", "ivolunteer", START_TIME)));

    assertEquals(EventTypeModel.OTHER, capturedSavedEvents().get(0).getEventType());
  }

  @Test
  @DisplayName("Unknown portal string maps to null portal — no crash")
  void unknownPortalMapsToNull() {
    when(eventRepository.existsByEventNameAndStartTime(any(), any())).thenReturn(false);

    eventImportService.importFromScrapeResponse(
        List.of(dto("Some Event", "OTHER", "unknown-portal-xyz", START_TIME)));

    assertNull(capturedSavedEvents().get(0).getPortal());
  }

  @Test
  @DisplayName("Null endTime is accepted and stored as null")
  void nullEndTimeIsAccepted() {
    when(eventRepository.existsByEventNameAndStartTime(any(), any())).thenReturn(false);
    ScrapeEventDTO noEnd =
        ScrapeEventDTO.builder()
            .name("Trek and Plog")
            .organisation("Org")
            .startTime(START_TIME)
            .endTime(null)
            .build();

    eventImportService.importFromScrapeResponse(List.of(noEnd));

    assertNull(capturedSavedEvents().get(0).getEndTime());
  }

  @Test
  @DisplayName("Duplicate event (same name + startTime) is skipped — count = 0")
  void skipsDuplicateEvent() {
    when(eventRepository.existsByEventNameAndStartTime("Trek and Plog", START_TIME))
        .thenReturn(true);

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

    when(eventRepository.existsByEventNameAndStartTime("Trek and Plog", t1)).thenReturn(true);
    when(eventRepository.existsByEventNameAndStartTime("Green Touch", t2)).thenReturn(false);

    int count =
        eventImportService.importFromScrapeResponse(
            List.of(
                dto("Trek and Plog", "TREKANDPLOG", "Team everest", t1),
                dto("Green Touch", "FOREST_CLEANUP", "ivolunteer", t2)));

    assertEquals(1, count);
    assertEquals("Green Touch", capturedSavedEvents().get(0).getEventName());
  }

  @Test
  @DisplayName("Event with blank name is silently skipped")
  void skipsEventWithBlankName() {
    ScrapeEventDTO blankName =
        ScrapeEventDTO.builder().name("").organisation("Org").startTime(START_TIME).build();

    int count = eventImportService.importFromScrapeResponse(List.of(blankName));

    assertEquals(0, count);
    verify(eventRepository, never()).existsByEventNameAndStartTime(any(), any());
  }

  @Test
  @DisplayName("Event with null startTime is silently skipped")
  void skipsEventWithNullStartTime() {
    ScrapeEventDTO noStart =
        ScrapeEventDTO.builder().name("Some Event").organisation("Org").startTime(null).build();

    int count = eventImportService.importFromScrapeResponse(List.of(noStart));

    assertEquals(0, count);
    verify(eventRepository, never()).existsByEventNameAndStartTime(any(), any());
  }

  @Test
  @DisplayName("Returns 0 immediately for null or empty list")
  void returnsZeroForNullOrEmptyList() {
    assertEquals(0, eventImportService.importFromScrapeResponse(null));
    assertEquals(0, eventImportService.importFromScrapeResponse(List.of()));
    verifyNoInteractions(eventRepository);
  }
}
