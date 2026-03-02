package org.smalltech.hashtaglocal_backend.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.smalltech.hashtaglocal_backend.dto.ScrapeResponseDTO;
import org.smalltech.hashtaglocal_backend.job.EventGeocodingJob;
import org.smalltech.hashtaglocal_backend.service.EventImportService;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Admin-only REST controller for importing events from the scrape service.
 *
 * <p>Base path: {@code /admin} — this should be protected by network rules or security config so it
 * is not publicly accessible.
 *
 * <p>Usage: the external scrape service calls {@code POST /admin/events/import} with the events
 * JSON payload after scraping each portal.
 */
@Tag(
    name = "Admin - Event Import",
    description = "Admin endpoints for importing and geocoding events")
@RestController
@RequestMapping("/admin")
@RequiredArgsConstructor
@Slf4j
public class EventImportController {

  private final EventImportService eventImportService;
  private final EventGeocodingJob eventGeocodingJob;

  /**
   * Accepts a JSON payload from the scrape service and imports new events, skipping duplicates.
   *
   * <p>Deduplication key: {@code eventName + startTime}. Events that already exist in the database
   * are silently skipped.
   *
   * <p>{@code POST /admin/events/import}
   */
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
  @PostMapping("/events/import")
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

  /**
   * Triggers forward geocoding for all events that have a raw address but no linked location.
   *
   * <p>Calls Google Maps Geocoding API for each event, creates a {@code Location} row, and links it
   * back to the event. Rate-limited to ~10 requests/second.
   *
   * <p>{@code POST /admin/events/geocode}
   */
  @Operation(
      summary = "Geocode event addresses",
      description =
          "For every event with a raw address and no lat/lng, calls Google Maps to resolve"
              + " coordinates, creates a Location row, and links it to the event.")
  @ApiResponse(
      responseCode = "200",
      description = "Job completed — returns total/success/failed counts")
  @PostMapping("/events/geocode")
  public ResponseEntity<EventGeocodingJob.GeocodingJobResult> geocodeEvents() {
    return ResponseEntity.ok(eventGeocodingJob.run());
  }
}
