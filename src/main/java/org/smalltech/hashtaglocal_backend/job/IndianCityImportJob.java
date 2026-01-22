package org.smalltech.hashtaglocal_backend.job;

import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.locationtech.jts.geom.Polygon;
import org.smalltech.hashtaglocal_backend.entity.CityImportStatus;
import org.smalltech.hashtaglocal_backend.entity.ImportJob;
import org.smalltech.hashtaglocal_backend.entity.Locality;
import org.smalltech.hashtaglocal_backend.repository.CityImportStatusRepository;
import org.smalltech.hashtaglocal_backend.repository.ImportJobRepository;
import org.smalltech.hashtaglocal_backend.repository.LocalityRepository;
import org.smalltech.hashtaglocal_backend.service.IndianCityPolygonService;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

/**
 * Import job to fetch and store Indian city polygons into the Localities table.
 * This can be run as a one-time script or scheduled job.
 *
 * To run: ./gradlew bootRun --args='--import-cities=true'
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class IndianCityImportJob implements CommandLineRunner {

	private final IndianCityPolygonService polygonService;
	private final LocalityRepository localityRepository;
	private final ImportJobRepository importJobRepository;
	private final CityImportStatusRepository cityImportStatusRepository;

	@Override
	public void run(String... args) throws Exception {
		// Check if the import-cities flag is set
		boolean shouldImport = false;
		for (String arg : args) {
			if ("--import-cities=true".equals(arg)) {
				shouldImport = true;
				break;
			}
		}

		if (!shouldImport) {
			log.info("Skipping city import. Use --import-cities=true to run the import.");
			return;
		}

		log.info("Starting Indian cities import job...");
		importIndianCities();
		log.info("Indian cities import job completed.");
	}

	/**
	 * Imports all Indian cities with their polygon boundaries
	 */
	public void importIndianCities() {
		List<String> cities = polygonService.getIndianCities();

		// Create a new import job record
		ImportJob job = ImportJob.builder().startedAt(LocalDateTime.now()).totalCities(cities.size())
				.status(ImportJob.ImportJobStatus.RUNNING).build();
		job = importJobRepository.save(job);

		log.info("Started import job #{} for {} cities", job.getId(), cities.size());

		try {
			for (int i = 0; i < cities.size(); i++) {
				String cityName = cities.get(i);
				log.info("Processing city {}/{}: {}", i + 1, cities.size(), cityName);

				importCityWithTracking(cityName, job);

				// Respect rate limiting (Google Maps API - 10 requests per second)
				polygonService.respectRateLimit();
			}

			// Mark job as completed
			job.setCompletedAt(LocalDateTime.now());
			job.setStatus(ImportJob.ImportJobStatus.COMPLETED);
			importJobRepository.save(job);

		} catch (Exception e) {
			log.error("Import job failed with error", e);
			job.setCompletedAt(LocalDateTime.now());
			job.setStatus(ImportJob.ImportJobStatus.FAILED);
			job.setErrorMessage(e.getMessage());
			importJobRepository.save(job);
		}

		// Print summary
		printJobSummary(job);
	}

	/**
	 * Imports a single city with tracking
	 */
	private CityImportStatus importCityWithTracking(String cityName, ImportJob job) {
		CityImportStatus cityStatus = CityImportStatus.builder().importJob(job).cityName(cityName)
				.attemptedAt(LocalDateTime.now()).status(CityImportStatus.CityImportResult.PENDING).build();

		try {
			String hashtag = polygonService.createHashtag(cityName);

			// Check if already exists
			if (localityRepository.findByHashtag(hashtag).isPresent()) {
				log.info("City {} already exists with hashtag {}. Skipping...", cityName, hashtag);
				cityStatus.setStatus(CityImportStatus.CityImportResult.SKIPPED);
				cityStatus.setCompletedAt(LocalDateTime.now());
				job.setSkippedCount(job.getSkippedCount() + 1);
				cityImportStatusRepository.save(cityStatus);
				importJobRepository.save(job);
				return cityStatus;
			}

			// Fetch polygon boundary
			Polygon polygon = polygonService.fetchCityPolygon(cityName);

			if (polygon != null) {
				// Create and save locality
				Locality locality = Locality.builder().name(cityName).hashtag(hashtag).geoBoundary(polygon).build();
				locality = localityRepository.save(locality);

				cityStatus.setLocality(locality);
				cityStatus.setStatus(CityImportStatus.CityImportResult.SUCCESS);
				cityStatus.setCompletedAt(LocalDateTime.now());
				job.setSuccessCount(job.getSuccessCount() + 1);

				log.info("Successfully imported city: {} with hashtag: {}", cityName, hashtag);
			} else {
				log.warn("No polygon data found for city: {}", cityName);
				cityStatus.setStatus(CityImportStatus.CityImportResult.NO_DATA_FOUND);
				cityStatus.setCompletedAt(LocalDateTime.now());
				cityStatus.setErrorMessage("No polygon data returned from Google Maps API");
				job.setFailureCount(job.getFailureCount() + 1);
			}

		} catch (Exception e) {
			log.error("Error importing city: {}", cityName, e);
			cityStatus.setStatus(CityImportStatus.CityImportResult.FAILED);
			cityStatus.setCompletedAt(LocalDateTime.now());
			cityStatus.setErrorMessage(e.getMessage());
			job.setFailureCount(job.getFailureCount() + 1);
		}

		cityImportStatusRepository.save(cityStatus);
		importJobRepository.save(job);
		return cityStatus;
	}

	/**
	 * Prints summary of import job
	 */
	private void printJobSummary(ImportJob job) {
		List<CityImportStatus> failedCities = cityImportStatusRepository.findByStatusIn(
				List.of(CityImportStatus.CityImportResult.FAILED, CityImportStatus.CityImportResult.NO_DATA_FOUND));

		log.info("\n========== Import Job #{} Summary ==========", job.getId());
		log.info("Status: {}", job.getStatus());
		log.info("Started: {}", job.getStartedAt());
		log.info("Completed: {}", job.getCompletedAt());
		log.info("Total cities: {}", job.getTotalCities());
		log.info("Successfully imported: {}", job.getSuccessCount());
		log.info("Skipped (already exist): {}", job.getSkippedCount());
		log.info("Failed: {}", job.getFailureCount());

		if (!failedCities.isEmpty()) {
			log.warn("Failed cities ({}): ", failedCities.size());
			for (CityImportStatus status : failedCities) {
				log.warn("  - {} ({}): {}", status.getCityName(), status.getStatus(),
						status.getErrorMessage() != null ? status.getErrorMessage() : "Unknown error");
			}
			log.info("\nTo retry failed cities, use: POST /api/admin/localities/retry-failed-cities");
		}

		log.info("============================================\n");
	}

	/**
	 * Imports a single city (useful for testing or adding specific cities)
	 */
	public void importCity(String cityName) {
		log.info("Importing single city: {}", cityName);

		// Create a single-city import job
		ImportJob job = ImportJob.builder().startedAt(LocalDateTime.now()).totalCities(1)
				.status(ImportJob.ImportJobStatus.RUNNING).build();
		job = importJobRepository.save(job);

		try {
			importCityWithTracking(cityName, job);

			job.setCompletedAt(LocalDateTime.now());
			job.setStatus(ImportJob.ImportJobStatus.COMPLETED);
			importJobRepository.save(job);

		} catch (Exception e) {
			log.error("Error importing city: {}", cityName, e);
			job.setCompletedAt(LocalDateTime.now());
			job.setStatus(ImportJob.ImportJobStatus.FAILED);
			job.setErrorMessage(e.getMessage());
			importJobRepository.save(job);
		}

		printJobSummary(job);
	}

	/**
	 * Retries all failed city imports
	 */
	public ImportJob retryFailedCities() {
		List<CityImportStatus> failedStatuses = cityImportStatusRepository.findByStatusIn(
				List.of(CityImportStatus.CityImportResult.FAILED, CityImportStatus.CityImportResult.NO_DATA_FOUND));

		if (failedStatuses.isEmpty()) {
			log.info("No failed cities to retry");
			return null;
		}

		log.info("Found {} failed cities to retry", failedStatuses.size());

		// Create a new import job for retries
		ImportJob retryJob = ImportJob.builder().startedAt(LocalDateTime.now()).totalCities(failedStatuses.size())
				.status(ImportJob.ImportJobStatus.RUNNING).build();
		retryJob = importJobRepository.save(retryJob);

		try {
			for (int i = 0; i < failedStatuses.size(); i++) {
				CityImportStatus oldStatus = failedStatuses.get(i);
				String cityName = oldStatus.getCityName();

				log.info("Retrying city {}/{}: {} (previous attempt: {})", i + 1, failedStatuses.size(), cityName,
						oldStatus.getStatus());

				// Update attempt count on old status
				oldStatus.setAttemptCount(oldStatus.getAttemptCount() + 1);
				cityImportStatusRepository.save(oldStatus);

				// Try importing again
				importCityWithTracking(cityName, retryJob);

				// Respect rate limiting
				polygonService.respectRateLimit();
			}

			retryJob.setCompletedAt(LocalDateTime.now());
			retryJob.setStatus(ImportJob.ImportJobStatus.COMPLETED);
			importJobRepository.save(retryJob);

		} catch (Exception e) {
			log.error("Retry job failed with error", e);
			retryJob.setCompletedAt(LocalDateTime.now());
			retryJob.setStatus(ImportJob.ImportJobStatus.FAILED);
			retryJob.setErrorMessage(e.getMessage());
			importJobRepository.save(retryJob);
		}

		printJobSummary(retryJob);
		return retryJob;
	}

	/**
	 * Gets all import jobs
	 */
	public List<ImportJob> getAllImportJobs() {
		return importJobRepository.findAllByOrderByStartedAtDesc();
	}

	/**
	 * Gets specific import job details
	 */
	public ImportJob getImportJob(Long jobId) {
		return importJobRepository.findById(jobId).orElse(null);
	}

	/**
	 * Gets cities for a specific import job
	 */
	public List<CityImportStatus> getCitiesForJob(Long jobId) {
		ImportJob job = importJobRepository.findById(jobId).orElse(null);
		if (job == null) {
			return List.of();
		}
		return cityImportStatusRepository.findByImportJob(job);
	}
}
