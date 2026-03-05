package org.smalltech.hashtaglocal_backend.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.smalltech.hashtaglocal_backend.dto.ScrapeResponseDTO;
import org.smalltech.hashtaglocal_backend.job.EventGeocodingJob;
import org.smalltech.hashtaglocal_backend.job.EventIngestionCronJob;
import org.smalltech.hashtaglocal_backend.model.NewAPIResponse;
import org.smalltech.hashtaglocal_backend.model.response.EventData;
import org.smalltech.hashtaglocal_backend.model.response.EventListResponseData;
import org.smalltech.hashtaglocal_backend.service.EventImportService;
import org.smalltech.hashtaglocal_backend.service.EventService;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
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
  private final EventGeocodingJob eventGeocodingJob;
  private final EventIngestionCronJob eventIngestionCronJob;

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

  @Operation(
      summary = "Import events from scrape service",
      description =
          "Accepts the scrape service JSON payload and imports new events."
              + " Events with the same name + start_time are deduplicated.")
  @ApiResponses({
    @ApiResponse(
        responseCode = "200",
        description = "Import succeeded — returns count of newly imported events",
        content =
            @Content(
                mediaType = MediaType.TEXT_PLAIN_VALUE,
                schema = @Schema(example = "Imported 7 events successfully."))),
    @ApiResponse(
        responseCode = "400",
        description = "Import failed — returns error message",
        content =
            @Content(
                mediaType = MediaType.TEXT_PLAIN_VALUE,
                schema = @Schema(example = "Import failed: ...")))
  })
  @PostMapping("/api/v1/events/import")
  public ResponseEntity<String> importEvents(@RequestBody ScrapeResponseDTO body) {
    try {
      var events = body.getData() != null ? body.getData().getEvents() : null;
      int count = eventImportService.importFromScrapeResponse(events);
      return ResponseEntity.ok("Imported " + count + " events successfully.");
    } catch (Exception e) {
      log.error("Event import failed", e);
      return ResponseEntity.badRequest().body("Import failed: " + e.getMessage());
    }
  }

  @Operation(
      summary = "Geocode event addresses",
      description =
          "For every event with a raw address and no lat/lng, calls Google Maps to resolve"
              + " coordinates, creates a Location row, and links it to the event.")
  @ApiResponse(
      responseCode = "200",
      description = "Job completed — returns total/success/failed counts")
  @PostMapping("/api/v1/events/geocode")
  public ResponseEntity<EventGeocodingJob.GeocodingJobResult> geocodeEvents() {
    return ResponseEntity.ok(eventGeocodingJob.run());
  }

  @Operation(
      summary = "Manually trigger the event ingestion pipeline",
      description =
          "Runs the same pipeline as the scheduled cron job: fetches events from the scrape"
              + " service, imports new ones, then geocodes any un-resolved addresses.")
  @ApiResponse(responseCode = "200", description = "Pipeline completed — returns a summary")
  @PostMapping("/api/v1/events/ingest")
  public ResponseEntity<String> triggerIngestion() {
    eventIngestionCronJob.run();
    return ResponseEntity.ok("Ingestion pipeline completed. Check logs for details.");
  }
}
