package org.smalltech.hashtaglocal_backend.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.smalltech.hashtaglocal_backend.service.EventImportService;
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
@RestController
@RequestMapping("/admin")
@RequiredArgsConstructor
@Slf4j
public class EventImportController {

  private final EventImportService eventImportService;

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
  @PostMapping("/events/import")
  public ResponseEntity<String> importEvents(@RequestParam("file") MultipartFile file) {
    try {
      int count = eventImportService.importFromCsv(file);
      return ResponseEntity.ok("Imported " + count + " events successfully.");
    } catch (Exception e) {
      log.error("Event import failed", e);
      return ResponseEntity.badRequest().body("Import failed: " + e.getMessage());
    }
  }
}
