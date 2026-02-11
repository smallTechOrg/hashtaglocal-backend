package org.smalltech.hashtaglocal_backend.job;

import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.smalltech.hashtaglocal_backend.dto.LocationMetadataDTO;
import org.smalltech.hashtaglocal_backend.entity.Location;
import org.smalltech.hashtaglocal_backend.repository.LocationRepository;
import org.smalltech.hashtaglocal_backend.service.GoogleMapsGeocodingService;
import org.smalltech.hashtaglocal_backend.util.LocationUtil;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Job to update location metadata and names using Google Maps Geocoding API.
 * Processes locations in batches to update missing or incomplete metadata.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class LocationMetadataUpdateJob {

	private final LocationRepository locationRepository;
	private final GoogleMapsGeocodingService geocodingService;

	// Google Maps API has rate limits: ~50 requests/second, consider adjusting if
	// needed
	private static final int BATCH_SIZE = 100;
	private static final long DELAY_BETWEEN_REQUESTS_MS = 100; // ~10 requests/second to be safe

	/**
	 * Run the job to update all locations with missing or incomplete metadata.
	 *
	 * @return Summary of the update job
	 */
	@Transactional
	public LocationUpdateJobResult runFullUpdate() {
		log.info("🚀 Starting location metadata update job...");

		List<Location> allLocations = locationRepository.findAll();
		log.info("📊 Found {} total locations to process", allLocations.size());

		return processLocations(allLocations);
	}

	/**
	 * Run the job to update only locations with missing metadata.
	 *
	 * @return Summary of the update job
	 */
	@Transactional
	public LocationUpdateJobResult runPartialUpdate() {
		log.info("🚀 Starting partial location metadata update job (missing metadata only)...");

		List<Location> allLocations = locationRepository.findAll();
		List<Location> locationsToUpdate = allLocations.stream().filter(loc -> loc.getMetaData() == null
				|| loc.getMetaData().isEmpty() || !hasCompleteMetadata(loc.getMetaData())).toList();

		log.info("📊 Found {} locations with incomplete metadata out of {} total", locationsToUpdate.size(),
				allLocations.size());

		return processLocations(locationsToUpdate);
	}

	/**
	 * Process a list of locations and update their metadata.
	 */
	private LocationUpdateJobResult processLocations(List<Location> locations) {
		int successCount = 0;
		int failureCount = 0;
		int skippedCount = 0;

		for (int i = 0; i < locations.size(); i++) {
			Location location = locations.get(i);

			try {
				// Extract coordinates from Point geometry
				double latitude = LocationUtil.getLatitude(location.getPoint());
				double longitude = LocationUtil.getLongitude(location.getPoint());

				log.debug("Processing location {}/{}: ID={}, lat={}, lng={}", i + 1, locations.size(), location.getId(),
						latitude, longitude);

				// Get metadata from Google Maps
				LocationMetadataDTO metadata = geocodingService.reverseGeocode(latitude, longitude);

				if (metadata == null) {
					log.warn("⚠️  No metadata returned for location ID={}", location.getId());
					failureCount++;
					continue;
				}

				// Update location metadata
				Map<String, Object> metadataMap = geocodingService.metadataToMap(metadata);
				location.setMetaData(metadataMap);

				// Update location name if we have a good one
				if (metadata.getName() != null && !metadata.getName().isEmpty()) {
					location.setName(metadata.getName());
				} else if (metadata.getCity() != null) {
					location.setName(metadata.getCity());
				}

				locationRepository.save(location);
				successCount++;

				log.info("✅ Updated location ID={}: name='{}', city='{}'", location.getId(), location.getName(),
						metadata.getCity());

				// Rate limiting: sleep between requests
				if (i < locations.size() - 1) {
					Thread.sleep(DELAY_BETWEEN_REQUESTS_MS);
				}

				// Log progress every batch
				if ((i + 1) % BATCH_SIZE == 0) {
					log.info("📈 Progress: {}/{} locations processed", i + 1, locations.size());
				}

			} catch (InterruptedException e) {
				log.error("❌ Job interrupted at location {}/{}", i + 1, locations.size());
				Thread.currentThread().interrupt();
				break;
			} catch (Exception e) {
				log.error("❌ Error processing location ID={}: {}", location.getId(), e.getMessage());
				failureCount++;
			}
		}

		LocationUpdateJobResult result = new LocationUpdateJobResult(locations.size(), successCount, failureCount,
				skippedCount);

		log.info("🎉 Location metadata update job completed: {}", result);
		return result;
	}

	/**
	 * Check if metadata has all required fields.
	 */
	private boolean hasCompleteMetadata(Map<String, Object> metadata) {
		if (metadata == null || metadata.isEmpty()) {
			return false;
		}

		// Check for essential fields
		return metadata.containsKey("city") || metadata.containsKey("formatted_address");
	}

	/**
	 * Result summary for the location update job.
	 */
	public record LocationUpdateJobResult(int totalProcessed, int successCount, int failureCount, int skippedCount) {
		@Override
		public String toString() {
			return String.format("Total=%d, Success=%d, Failed=%d, Skipped=%d", totalProcessed, successCount,
					failureCount, skippedCount);
		}
	}
}
