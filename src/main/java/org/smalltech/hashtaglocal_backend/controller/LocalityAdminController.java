package org.smalltech.hashtaglocal_backend.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.smalltech.hashtaglocal_backend.entity.ImportJob;
import org.smalltech.hashtaglocal_backend.entity.LocalityDiscoveryRun;
import org.smalltech.hashtaglocal_backend.repository.DiscoveredLocalityRepository;
import org.smalltech.hashtaglocal_backend.repository.ImportJobRepository;
import org.smalltech.hashtaglocal_backend.repository.LocalityDiscoveryRunRepository;
import org.smalltech.hashtaglocal_backend.service.discovery.LocalityDiscoveryOrchestrator;
import org.smalltech.hashtaglocal_backend.service.import_job.LocalityImportService;
import org.springframework.web.bind.annotation.*;

/**
 * Admin API endpoints for locality discovery and import workflows.
 *
 * <p>Phase 1 (Discovery): - POST /admin/discovery/start - Start discovery run - GET
 * /admin/discovery/status/{id} - Check discovery progress
 *
 * <p>Phase 2 (Import): - POST /admin/import/start - Start import job - GET
 * /admin/import/status/{id} - Check import progress
 */
@RestController
@RequestMapping("/admin")
@RequiredArgsConstructor
@Slf4j
public class LocalityAdminController {

  private final LocalityDiscoveryOrchestrator discoveryOrchestrator;
  private final LocalityImportService importService;
  private final LocalityDiscoveryRunRepository discoveryRunRepository;
  private final ImportJobRepository importJobRepository;
  private final DiscoveredLocalityRepository discoveredLocalityRepository;

  /**
   * Start locality discovery for a country.
   *
   * <p>Queries GeoNames, OSM, and deduplicates results for all locality types.
   */
  @PostMapping("/discovery/start")
  public LocalityDiscoveryRun startDiscovery(@RequestParam String countryCode) {
    log.info("🚀 Starting comprehensive discovery for {}", countryCode);
    // Single run to deduplicate across city/town/district
    LocalityDiscoveryRun run = discoveryOrchestrator.discoverAll(countryCode);
    log.info(
        "✅ Complete discovery finished: totalRaw={} geonames={} osm={}",
        run.getTotalRawDiscoveries(),
        run.getGeonamesCount(),
        run.getOsmCount());
    return run;
  }

  /** Get discovery run status. */
  @GetMapping("/discovery/status/{id}")
  public LocalityDiscoveryRun getDiscoveryStatus(@PathVariable Long id) {
    return discoveryRunRepository
        .findById(id)
        .orElseThrow(() -> new RuntimeException("Discovery run not found: " + id));
  }

  /** Start import job for discovered localities. */
  @PostMapping("/import/start")
  public ImportJob startImport(
      @RequestParam Long discoveryRunId, @RequestParam String localityType) {
    log.info(
        "🚀 Starting import job for discovery run {}, localityType={}",
        discoveryRunId,
        localityType);

    LocalityDiscoveryRun discoveryRun =
        discoveryRunRepository
            .findById(discoveryRunId)
            .orElseThrow(() -> new RuntimeException("Discovery run not found: " + discoveryRunId));

    // Get discovered localities matching the type
    var localities =
        discoveredLocalityRepository.findByDiscoveryRunAndLocalityType(
            discoveryRun,
            org.smalltech.hashtaglocal_backend.entity.DiscoveredLocality.LocalityType.valueOf(
                localityType));

    log.info("📥 Found {} localities of type {} to import", localities.size(), localityType);

    // Create import job
    ImportJob job =
        ImportJob.builder()
            .startedAt(java.time.LocalDateTime.now())
            .status(ImportJob.ImportJobStatus.RUNNING)
            .totalLocalities(localities.size())
            .build();

    job = importJobRepository.save(job);

    // Process imports synchronously for now
    int successCount = 0;
    int failureCount = 0;
    int skippedCount = 0;

    for (var locality : localities) {
      try {
        var status = importService.validateAndImport(job, locality);
        if (status.getImportStatus()
            == org.smalltech.hashtaglocal_backend.entity.LocalityImportStatus.ImportStatus
                .SUCCESS) {
          successCount++;
        } else if (status.getImportStatus()
            == org.smalltech.hashtaglocal_backend.entity.LocalityImportStatus.ImportStatus
                .NO_DATA_FOUND) {
          skippedCount++;
        } else {
          failureCount++;
        }
      } catch (Exception e) {
        log.error("❌ Failed to import {}: {}", locality.getOfficialName(), e.getMessage());
        failureCount++;
      }
    }

    // Update job with final counts
    job.setCompletedAt(java.time.LocalDateTime.now());
    job.setStatus(ImportJob.ImportJobStatus.COMPLETED);
    job.setSuccessCount(successCount);
    job.setFailureCount(failureCount);
    job.setSkippedCount(skippedCount);
    job = importJobRepository.save(job);

    log.info(
        "✅ Import job completed: Success={}, Failed={}, Skipped={}",
        successCount,
        failureCount,
        skippedCount);

    return job;
  }

  /** Get import job status. */
  @GetMapping("/import/status/{id}")
  public ImportJob getImportStatus(@PathVariable Long id) {
    return importJobRepository
        .findById(id)
        .orElseThrow(() -> new RuntimeException("Import job not found: " + id));
  }
}
