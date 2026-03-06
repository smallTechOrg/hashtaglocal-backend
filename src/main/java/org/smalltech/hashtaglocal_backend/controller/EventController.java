package org.smalltech.hashtaglocal_backend.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.smalltech.hashtaglocal_backend.model.NewAPIResponse;
import org.smalltech.hashtaglocal_backend.model.response.EventData;
import org.smalltech.hashtaglocal_backend.model.response.EventListResponseData;
import org.smalltech.hashtaglocal_backend.service.EventService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Tag(name = "Event", description = "Event APIs")
@RequiredArgsConstructor
public class EventController {

  private final EventService eventService;

  @GetMapping("/api/v1/events")
  @Operation(
      summary = "List all events",
      description = "Returns events that have a resolved location.")
  public NewAPIResponse<EventListResponseData> getEvents() {
    List<EventData> events = eventService.getAllAsEventData();
    return NewAPIResponse.<EventListResponseData>builder()
        .data(EventListResponseData.builder().events(events).build())
        .build();
  }
}
