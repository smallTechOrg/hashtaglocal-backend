package org.smalltech.hashtaglocal_backend.service.import_job;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.Polygon;
import org.smalltech.hashtaglocal_backend.dto.GoogleMapsLocationDTO;
import org.smalltech.hashtaglocal_backend.entity.DiscoveredLocality;
import org.smalltech.hashtaglocal_backend.entity.ImportJob;
import org.smalltech.hashtaglocal_backend.entity.Locality;
import org.smalltech.hashtaglocal_backend.entity.LocalityImportStatus;
import org.smalltech.hashtaglocal_backend.repository.LocalityImportStatusRepository;
import org.smalltech.hashtaglocal_backend.repository.LocalityRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;

/**
 * Handles locality import with Google Maps validation.
 *
 * Phase 2 of the workflow: 1. Validate locality with Google Maps Geocoding API
 * 2. Extract polygon from viewport 3. Save to localities table 4. Track import
 * status with retry logic
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class LocalityImportService {

	private final LocalityImportStatusRepository importStatusRepository;
	private final LocalityRepository localityRepository;
	private final RestTemplate restTemplate;
	private final GeometryFactory geometryFactory = new GeometryFactory();

	@Value("${google.maps.api-key:}")
	private String googleMapsApiKey;

	private static final String GOOGLE_MAPS_GEOCODING_URL = "https://maps.googleapis.com/maps/api/geocode/json?address=%s&key=%s";
	private static final ObjectMapper objectMapper = new ObjectMapper();

	/**
	 * Validate locality with Google Maps and create import status record.
	 */
	@Transactional
	public LocalityImportStatus validateAndImport(ImportJob job, DiscoveredLocality discovered) {
		LocalityImportStatus status = LocalityImportStatus.builder().importJob(job).discoveredLocality(discovered)
				.localityName(discovered.getOfficialName()).state(discovered.getState())
				.localityType(LocalityImportStatus.LocalityType.valueOf(discovered.getLocalityType().toString()))
				.attemptCount(1).build();

		try {
			log.info("📥 Validating {} with Google Maps", discovered.getOfficialName());

			// Build query address
			String query = String.format("%s, %s, India", discovered.getOfficialName(), discovered.getState());

			// Query Google Maps
			String url = String.format(GOOGLE_MAPS_GEOCODING_URL, encodeUrl(query), googleMapsApiKey);
			GoogleMapsLocationDTO response = restTemplate.getForObject(url, GoogleMapsLocationDTO.class);

			if (response == null || response.getResults() == null || response.getResults().isEmpty()) {
				status.setImportStatus(LocalityImportStatus.ImportStatus.NO_DATA_FOUND);
				log.warn("⚠️  No results from Google Maps for {}", discovered.getOfficialName());
				return importStatusRepository.save(status);
			}

			// Extract first result
			GoogleMapsLocationDTO.Result result = response.getResults().get(0);

			// Store full Google Maps response as JSON metadata
			status.setGoogleMapsMetadata(objectMapper.writeValueAsString(result));

			// Extract geometry for polygon creation
			GoogleMapsLocationDTO.Geometry geometry = result.getGeometry();
			if (geometry != null && geometry.getViewport() != null) {
				GoogleMapsLocationDTO.Viewport viewport = geometry.getViewport();
				GoogleMapsLocationDTO.Location ne = viewport.getNortheast();
				GoogleMapsLocationDTO.Location sw = viewport.getSouthwest();

				if (ne != null && sw != null) {
					// Create polygon from viewport bounds (rectangle)
					Coordinate[] coords = new Coordinate[]{new Coordinate(sw.getLng(), sw.getLat()),
							new Coordinate(ne.getLng(), sw.getLat()), new Coordinate(ne.getLng(), ne.getLat()),
							new Coordinate(sw.getLng(), ne.getLat()), new Coordinate(sw.getLng(), sw.getLat()) // Close
																												// polygon
					};
					Polygon boundary = geometryFactory.createPolygon(coords);
					boundary.setSRID(4326);

					// Create hashtag (lowercase, remove spaces)
					String hashtag = "#" + discovered.getOfficialName().toLowerCase().replaceAll("\\s+", "");

					// Create locality in localities table
					Locality locality = Locality.builder().hashtag(hashtag).name(discovered.getOfficialName())
							.geoBoundary(boundary).build();
					locality = localityRepository.save(locality);

					// Link to import status
					status.setLocality(locality);
				}
			}

			status.setImportStatus(LocalityImportStatus.ImportStatus.SUCCESS);
			log.info("✅ Successfully imported {} with hashtag {}", discovered.getOfficialName(),
					status.getLocality() != null ? status.getLocality().getHashtag() : "N/A");

		} catch (Exception e) {
			status.setImportStatus(LocalityImportStatus.ImportStatus.FAILED);
			status.setErrorMessage(e.getMessage());
			log.error("❌ Import failed for {}: {}", discovered.getOfficialName(), e.getMessage());
		}

		return importStatusRepository.save(status);
	}

	/**
	 * Retry failed import with incremented attempt count.
	 */
	@Transactional
	public LocalityImportStatus retry(LocalityImportStatus failedStatus) {
		failedStatus.setAttemptCount(failedStatus.getAttemptCount() + 1);

		if (failedStatus.getAttemptCount() > 3) {
			failedStatus.setImportStatus(LocalityImportStatus.ImportStatus.FAILED);
			failedStatus.setErrorMessage("Max retries exceeded");
			log.warn("⚠️  Max retries exceeded for {}", failedStatus.getLocalityName());
			return importStatusRepository.save(failedStatus);
		}

		// Re-query if we still have attempts
		ImportJob job = failedStatus.getImportJob();
		DiscoveredLocality discovered = failedStatus.getDiscoveredLocality();

		return validateAndImport(job, discovered);
	}

	private String encodeUrl(String input) {
		try {
			return java.net.URLEncoder.encode(input, java.nio.charset.StandardCharsets.UTF_8);
		} catch (Exception e) {
			return input;
		}
	}
}
