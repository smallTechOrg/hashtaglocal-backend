package org.smalltech.hashtaglocal_backend.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.smalltech.hashtaglocal_backend.dto.ScrapeEventDTO;
import org.smalltech.hashtaglocal_backend.model.NewAPIResponse;
import org.smalltech.hashtaglocal_backend.model.response.EventData;
import org.smalltech.hashtaglocal_backend.model.response.EventListResponseData;
import org.smalltech.hashtaglocal_backend.service.EventGeocodingService;
import org.smalltech.hashtaglocal_backend.service.EventImportService;
import org.smalltech.hashtaglocal_backend.service.EventService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Tag(name = "Event", description = "Event APIs")
@RequiredArgsConstructor
@Slf4j
public class EventController {

  private final EventService eventService;
  private final EventImportService eventImportService;
  private final EventGeocodingService eventGeocodingService;

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

  @PostMapping("/api/v1/events/ingest")
  @Operation(
      summary = "Manually ingest events",
      description =
          "Accepts the same payload as the web scraper response and runs the import + geocoding pipeline.")
  public NewAPIResponse<Map<String, Object>> ingestEvents(
      @RequestBody List<ScrapeEventDTO> events) {
    log.info("Manual event ingestion triggered with {} events", events.size());

    int imported = eventImportService.importFromScrapeResponse(events);
    log.info("Imported {} new events", imported);

    EventGeocodingService.GeocodingResult geocodeResult = eventGeocodingService.run();
    log.info("Geocoding complete: {}", geocodeResult);

    Map<String, Object> result =
        Map.of(
            "imported",
            imported,
            "geocoding",
            Map.of(
                "total", geocodeResult.total(),
                "success", geocodeResult.success(),
                "failed", geocodeResult.failed(),
                "localitiesLinked", geocodeResult.localitiesLinked()));

    return NewAPIResponse.<Map<String, Object>>builder().data(result).build();
  }
}
