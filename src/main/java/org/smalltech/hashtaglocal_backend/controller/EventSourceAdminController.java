package org.smalltech.hashtaglocal_backend.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.smalltech.hashtaglocal_backend.job.EventIngestionRunner;
import org.smalltech.hashtaglocal_backend.model.request.EventSourceInput;
import org.smalltech.hashtaglocal_backend.model.response.EventSourceData;
import org.smalltech.hashtaglocal_backend.model.response.EventSourceTestResult;
import org.smalltech.hashtaglocal_backend.service.EventSourceService;
import org.smalltech.hashtaglocal_backend.service.EventSourceService.DuplicateSourceException;
import org.smalltech.hashtaglocal_backend.service.EventSourceService.SourceNotFoundException;
import org.smalltech.hashtaglocal_backend.service.scraper0.Scraper0Exception;
import org.smalltech.hashtaglocal_backend.service.scraper0.Scraper0Models.SiteSummary;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * The scraper0 site actions that event ingestion reads, for the Ops Dashboard (Event sources). All
 * routes are under {@code /admin/**}, so SecurityConfig restricts them to ROLE_ADMIN.
 *
 * <p>Bodies are plain JSON (no {@code data} wrapper), snake_case like the rest of the API. Errors
 * are {@code {"message": …}}: 404 unknown source, 409 duplicate {@code site_id + action}, 502 a
 * scraper0 error as {@code "<scraper0 code>: <message>"}.
 */
@RestController
@RequestMapping("/admin/event-sources")
@Tag(
    name = "Admin — Event sources",
    description = "scraper0 sites that event ingestion reads, and their last run.")
@SecurityRequirement(name = "bearerAuth")
@RequiredArgsConstructor
@Slf4j
public class EventSourceAdminController {

  static final String ONBOARD_FIRST = "Onboard this site in scraper0 first";

  private final EventSourceService service;
  private final EventIngestionRunner ingestionRunner;

  @GetMapping
  @Operation(summary = "List event sources")
  public List<EventSourceData> list() {
    return service.list();
  }

  @PostMapping
  @Operation(summary = "Add an event source", description = "409 when site_id + action exists.")
  public ResponseEntity<EventSourceData> create(@Valid @RequestBody EventSourceInput input) {
    return ResponseEntity.status(HttpStatus.CREATED).body(service.create(input));
  }

  @PutMapping("/{id}")
  @Operation(summary = "Edit an event source")
  public EventSourceData update(@PathVariable Long id, @Valid @RequestBody EventSourceInput input) {
    return service.update(id, input);
  }

  @DeleteMapping("/{id}")
  @Operation(summary = "Delete an event source", description = "Imported events are kept.")
  public ResponseEntity<Void> delete(@PathVariable Long id) {
    service.delete(id);
    return ResponseEntity.noContent().build();
  }

  @PostMapping("/{id}/test")
  @Operation(
      summary = "Test an event source",
      description =
          "Reads the source from scraper0 now and returns the first 20 items. Imports nothing.")
  public EventSourceTestResult test(@PathVariable Long id) {
    return service.test(id);
  }

  @PostMapping("/run")
  @Operation(
      summary = "Run event ingestion now",
      description =
          "Runs ingestion for every enabled source and answers when it is done (200). 409 when a"
              + " run is already in progress.")
  public ResponseEntity<Map<String, Object>> run() {
    if (!ingestionRunner.runNow()) {
      return ResponseEntity.status(HttpStatus.CONFLICT)
          .body(Map.of("message", "An ingestion run is already in progress; try again shortly"));
    }
    return ResponseEntity.ok(Map.of("started", true, "finished", true));
  }

  @GetMapping("/scraper0-sites")
  @Operation(
      summary = "Sites onboarded in scraper0",
      description =
          "Proxied from scraper0 GET /v2/sites. Only sites with a locked read action can be used.")
  public List<SiteSummary> scraper0Sites(@RequestParam(required = false) String q) {
    return service.scraper0Sites(q);
  }

  // ---------------------------------------------------------------------------

  @ExceptionHandler(SourceNotFoundException.class)
  ResponseEntity<Map<String, String>> notFound(SourceNotFoundException e) {
    return message(HttpStatus.NOT_FOUND, e.getMessage());
  }

  @ExceptionHandler(DuplicateSourceException.class)
  ResponseEntity<Map<String, String>> duplicate(DuplicateSourceException e) {
    return message(HttpStatus.CONFLICT, e.getMessage());
  }

  @ExceptionHandler(Scraper0Exception.class)
  ResponseEntity<Map<String, String>> scraper0(Scraper0Exception e) {
    String text = e.isNotOnboarded() ? e.getCode() + ": " + ONBOARD_FIRST : e.describe();
    log.warn("[EVENT-SOURCES] scraper0 error: {}", e.describe());
    return message(HttpStatus.BAD_GATEWAY, text);
  }

  private static ResponseEntity<Map<String, String>> message(HttpStatus status, String text) {
    return ResponseEntity.status(status).body(Map.of("message", text));
  }
}
