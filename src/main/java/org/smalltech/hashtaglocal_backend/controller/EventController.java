package org.smalltech.hashtaglocal_backend.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.smalltech.hashtaglocal_backend.entity.EventEntity;
import org.smalltech.hashtaglocal_backend.entity.Location;
import org.smalltech.hashtaglocal_backend.model.NewAPIResponse;
import org.smalltech.hashtaglocal_backend.model.response.EventData;
import org.smalltech.hashtaglocal_backend.model.response.EventListResponseData;
import org.smalltech.hashtaglocal_backend.service.EventService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Public REST controller exposing event data to the frontend.
 *
 * <p>Base path: {@code /api/v1}
 *
 * <p>All responses are wrapped in {@link NewAPIResponse} so the JSON shape is always: {@code {
 * "data": { ... } }}.
 */
@RestController
@RequestMapping("/api/v1")
@Tag(name = "Event", description = "Event APIs")
@RequiredArgsConstructor
public class EventController {

  private final EventService eventService;

  /**
   * Returns all events stored in the database.
   *
   * <p>Each event includes location details (lat, lng, locality name) when geocoding has been done.
   * If {@code location} is null in the response, the {@code address} field holds the raw address
   * string as a fallback.
   *
   * <p>{@code GET /api/v1/events}
   */
  @GetMapping("/events")
  @Operation(summary = "List all events", description = "Returns all events with location details.")
  public NewAPIResponse<EventListResponseData> getEvents() {
    List<EventData> events = eventService.getAll().stream().map(this::toEventData).toList();

    return NewAPIResponse.<EventListResponseData>builder()
        .data(EventListResponseData.builder().events(events).build())
        .build();
  }

  /**
   * Converts a database {@link EventEntity} into the API response model {@link EventData}.
   *
   * <p>Extracts lat/lng from the PostGIS Point stored in the linked {@link Location}. If no
   * Location has been linked yet (geocoding pending), the {@code location} field in the response is
   * left null.
   */
  private EventData toEventData(EventEntity entity) {
    EventData.LocationData locationData = null;
    Location loc = entity.getLocation();
    if (loc != null) {
      // PostGIS Point: X = longitude, Y = latitude
      String localityName = loc.getLocality() != null ? loc.getLocality().getName() : null;
      locationData =
          EventData.LocationData.builder()
              .id(loc.getId())
              .lat(loc.getPoint().getY())
              .lng(loc.getPoint().getX())
              .name(loc.getName())
              .locality(localityName)
              .build();
    }

    return EventData.builder()
        .id(entity.getId())
        .name(entity.getEventName())
        .organisation(entity.getOrganisation())
        .imageUrl(entity.getImageUrl())
        .portal(entity.getPortal() != null ? entity.getPortal().name() : null)
        .type(entity.getEventType() != null ? entity.getEventType().name() : null)
        .startTime(entity.getStartTime())
        .endTime(entity.getEndTime())
        .location(locationData)
        .address(entity.getAddress())
        .link(entity.getLink())
        .metaData(entity.getMetaData())
        .build();
  }
}
