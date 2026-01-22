package org.smalltech.hashtaglocal_backend.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.smalltech.hashtaglocal_backend.entity.CityImportStatus;
import org.smalltech.hashtaglocal_backend.entity.ImportJob;
import org.smalltech.hashtaglocal_backend.job.IndianCityImportJob;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * Controller for managing locality data imports
 */
@Slf4j
@RestController
@RequestMapping("/api/admin/localities")
@RequiredArgsConstructor
@Tag(name = "Admin - Locality Import", description = "APIs for importing city polygon data")
public class LocalityImportController {

	private final IndianCityImportJob importJob;

	@PostMapping("/import-all-cities")
	@Operation(summary = "Import all Indian cities", description = "Fetches and imports polygon boundaries for all major Indian cities")
	public ResponseEntity<Map<String, Object>> importAllCities() {
		log.info("Received request to import all Indian cities");

		try {
			// Run import in a separate thread to avoid timeout
			new Thread(() -> {
				try {
					importJob.importIndianCities();
				} catch (Exception e) {
					log.error("Error in city import thread", e);
				}
			}).start();

			Map<String, Object> response = new HashMap<>();
			response.put("status", "started");
			response.put("message", "City import job started. Check logs for progress.");

			return ResponseEntity.accepted().body(response);

		} catch (Exception e) {
			log.error("Error starting city import", e);
			Map<String, Object> response = new HashMap<>();
			response.put("status", "error");
			response.put("message", e.getMessage());
			return ResponseEntity.internalServerError().body(response);
		}
	}

	@PostMapping("/import-city")
	@Operation(summary = "Import a single city", description = "Fetches and imports polygon boundary for a specific city")
	public ResponseEntity<Map<String, Object>> importCity(@RequestParam String cityName) {
		log.info("Received request to import city: {}", cityName);

		try {
			importJob.importCity(cityName);

			Map<String, Object> response = new HashMap<>();
			response.put("status", "success");
			response.put("message", "City import completed for: " + cityName);
			response.put("cityName", cityName);

			return ResponseEntity.ok(response);

		} catch (Exception e) {
			log.error("Error importing city: {}", cityName, e);
			Map<String, Object> response = new HashMap<>();
			response.put("status", "error");
			response.put("message", e.getMessage());
			response.put("cityName", cityName);
			return ResponseEntity.internalServerError().body(response);
		}
	}

	@PostMapping("/retry-failed-cities")
	@Operation(summary = "Retry failed city imports", description = "Retries importing all cities that previously failed")
	public ResponseEntity<Map<String, Object>> retryFailedCities() {
		log.info("Received request to retry failed cities");

		try {
			// Run retry in a separate thread to avoid timeout
			new Thread(() -> {
				try {
					importJob.retryFailedCities();
				} catch (Exception e) {
					log.error("Error in retry thread", e);
				}
			}).start();

			Map<String, Object> response = new HashMap<>();
			response.put("status", "started");
			response.put("message", "Retry job started. Check logs for progress.");

			return ResponseEntity.accepted().body(response);

		} catch (Exception e) {
			log.error("Error starting retry job", e);
			Map<String, Object> response = new HashMap<>();
			response.put("status", "error");
			response.put("message", e.getMessage());
			return ResponseEntity.internalServerError().body(response);
		}
	}

	@GetMapping("/import-jobs")
	@Operation(summary = "Get all import jobs", description = "Returns list of all import jobs with their status")
	public ResponseEntity<List<Map<String, Object>>> getAllImportJobs() {
		try {
			List<ImportJob> jobs = importJob.getAllImportJobs();
			List<Map<String, Object>> response = jobs.stream().map(this::convertJobToMap).collect(Collectors.toList());
			return ResponseEntity.ok(response);
		} catch (Exception e) {
			log.error("Error fetching import jobs", e);
			return ResponseEntity.internalServerError().build();
		}
	}

	@GetMapping("/import-jobs/{jobId}")
	@Operation(summary = "Get import job details", description = "Returns detailed information about a specific import job including all city statuses")
	public ResponseEntity<Map<String, Object>> getImportJobDetails(@PathVariable Long jobId) {
		try {
			ImportJob job = importJob.getImportJob(jobId);
			if (job == null) {
				return ResponseEntity.notFound().build();
			}

			List<CityImportStatus> cities = importJob.getCitiesForJob(jobId);

			Map<String, Object> response = convertJobToMap(job);
			response.put("cities", cities.stream().map(this::convertCityStatusToMap).collect(Collectors.toList()));

			return ResponseEntity.ok(response);
		} catch (Exception e) {
			log.error("Error fetching import job details", e);
			return ResponseEntity.internalServerError().build();
		}
	}

	private Map<String, Object> convertJobToMap(ImportJob job) {
		Map<String, Object> map = new HashMap<>();
		map.put("id", job.getId());
		map.put("status", job.getStatus());
		map.put("startedAt", job.getStartedAt());
		map.put("completedAt", job.getCompletedAt());
		map.put("totalCities", job.getTotalCities());
		map.put("successCount", job.getSuccessCount());
		map.put("failureCount", job.getFailureCount());
		map.put("skippedCount", job.getSkippedCount());
		map.put("errorMessage", job.getErrorMessage());
		return map;
	}

	private Map<String, Object> convertCityStatusToMap(CityImportStatus status) {
		Map<String, Object> map = new HashMap<>();
		map.put("id", status.getId());
		map.put("cityName", status.getCityName());
		map.put("status", status.getStatus());
		map.put("attemptedAt", status.getAttemptedAt());
		map.put("completedAt", status.getCompletedAt());
		map.put("attemptCount", status.getAttemptCount());
		map.put("errorMessage", status.getErrorMessage());
		if (status.getLocality() != null) {
			map.put("localityId", status.getLocality().getId());
			map.put("hashtag", status.getLocality().getHashtag());
		}
		return map;
	}
}
