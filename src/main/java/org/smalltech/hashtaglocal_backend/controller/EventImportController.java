package org.smalltech.hashtaglocal_backend.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.smalltech.hashtaglocal_backend.job.EventGeocodingJob;
import org.smalltech.hashtaglocal_backend.service.EventImportService;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * Admin-only REST controller for importing events from a CSV file.
 *
 * <p>Base path: {@code /admin} — this should be protected by network rules or security config so it
 * is not publicly accessible.
 *
 * <p>Usage: export the events Excel sheet as a UTF-8 CSV and upload it via:
 *
 * <pre>
 *   POST /admin/events/import
 *   Content-Type: multipart/form-data
 *   form field: file = &lt;your CSV file&gt;
 * </pre>
 *
 * <p>The import is idempotent in the sense that re-uploading the same CSV will insert duplicate
 * rows — there is currently no deduplication logic.
 */
@Tag(name = "Admin - Event Import", description = "Admin endpoints for bulk importing events")
@RestController
@RequestMapping("/admin")
@RequiredArgsConstructor
@Slf4j
public class EventImportController {

  private final EventImportService eventImportService;
  private final EventGeocodingJob eventGeocodingJob;

  /**
   * Accepts a CSV file upload and imports all valid rows as events.
   *
   * <p>Returns a plain-text summary of how many events were imported, or an error message if
   * parsing fails entirely. Individual bad rows are skipped with a warning log rather than failing
   * the whole request.
   *
   * <p>{@code POST /admin/events/import}
   *
   * @param file the CSV file (multipart form field named "file")
   */
  @Operation(
      summary = "Import events from CSV",
      description =
          "Upload a UTF-8 CSV file to bulk-import events. Duplicate rows are not deduplicated.")
  @ApiResponses({
    @ApiResponse(
        responseCode = "200",
        description = "Import succeeded — returns count of imported events",
        content =
            @Content(
                mediaType = MediaType.TEXT_PLAIN_VALUE,
                schema = @Schema(example = "Imported 42 events successfully."))),
    @ApiResponse(
        responseCode = "400",
        description = "Import failed — returns error message",
        content =
            @Content(
                mediaType = MediaType.TEXT_PLAIN_VALUE,
                schema = @Schema(example = "Import failed: ...")))
  })
  @PostMapping(value = "/events/import", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  public ResponseEntity<String> importEvents(
      @Parameter(description = "UTF-8 CSV file to import", required = true) @RequestParam("file")
          MultipartFile file) {
    try {
      int count = eventImportService.importFromCsv(file);
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
