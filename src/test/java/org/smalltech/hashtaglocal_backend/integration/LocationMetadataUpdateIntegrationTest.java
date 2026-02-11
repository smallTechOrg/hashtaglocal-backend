package org.smalltech.hashtaglocal_backend.integration;

import static org.junit.jupiter.api.Assertions.*;

import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.locationtech.jts.geom.Point;
import org.smalltech.hashtaglocal_backend.entity.Locality;
import org.smalltech.hashtaglocal_backend.entity.Location;
import org.smalltech.hashtaglocal_backend.job.LocationMetadataUpdateJob;
import org.smalltech.hashtaglocal_backend.repository.LocalityRepository;
import org.smalltech.hashtaglocal_backend.repository.LocationRepository;
import org.smalltech.hashtaglocal_backend.util.LocationUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

/**
 * Integration test for Location Metadata Update Job. Note: This test requires a
 * valid Google Maps API key to be configured. It will be skipped if the API key
 * is not set or if running in CI.
 */
@SpringBootTest
@ActiveProfiles("test")
@DisplayName("Location Metadata Update Integration Tests")
class LocationMetadataUpdateIntegrationTest {

	@Autowired
	private LocationRepository locationRepository;

	@Autowired
	private LocalityRepository localityRepository;

	@Autowired(required = false)
	private LocationMetadataUpdateJob updateJob;

	private Locality testLocality;

	@BeforeEach
	@Transactional
	void setUp() {
		// Create a test locality
		testLocality = Locality.builder().hashtag("#test-locality").name("Test Locality")
				.geoBoundary(LocationUtil.createPoint(12.9716, 77.5946).getFactory()
						.createPolygon(new org.locationtech.jts.geom.Coordinate[]{
								new org.locationtech.jts.geom.Coordinate(77.5, 12.9),
								new org.locationtech.jts.geom.Coordinate(77.6, 12.9),
								new org.locationtech.jts.geom.Coordinate(77.6, 13.0),
								new org.locationtech.jts.geom.Coordinate(77.5, 13.0),
								new org.locationtech.jts.geom.Coordinate(77.5, 12.9)}))
				.build();
		testLocality.getGeoBoundary().setSRID(4326);
		testLocality = localityRepository.save(testLocality);
	}

	@Test
	@Transactional
	@DisplayName("Should create location with metadata structure")
	void shouldCreateLocationWithMetadata() {
		// Create a test location with coordinates in Bengaluru
		Point point = LocationUtil.createPoint(12.9716, 77.5946); // Bengaluru coordinates
		Map<String, Object> metadata = new HashMap<>();
		metadata.put("city", "Bengaluru");
		metadata.put("region", "Karnataka");
		metadata.put("country", "India");
		metadata.put("iso_country_code", "IN");

		Location location = Location.builder().point(point).name("Test Location").locality(testLocality)
				.metaData(metadata).build();

		Location saved = locationRepository.save(location);

		assertNotNull(saved.getId());
		assertNotNull(saved.getMetaData());
		assertEquals("Bengaluru", saved.getMetaData().get("city"));
		assertEquals("Karnataka", saved.getMetaData().get("region"));
		assertEquals("India", saved.getMetaData().get("country"));
		assertEquals("IN", saved.getMetaData().get("iso_country_code"));
	}

	@Test
	@Transactional
	@DisplayName("Should handle location with null metadata")
	void shouldHandleLocationWithNullMetadata() {
		// Create location without metadata
		Point point = LocationUtil.createPoint(12.9716, 77.5946);
		Location location = Location.builder().point(point).name("Location Without Metadata").locality(testLocality)
				.build();

		Location saved = locationRepository.save(location);

		assertNotNull(saved.getId());
		assertNull(saved.getMetaData());
	}

	@Test
	@Transactional
	@DisplayName("Should retrieve and verify metadata structure")
	void shouldRetrieveAndVerifyMetadataStructure() {
		// Create location with complete metadata structure
		Point point = LocationUtil.createPoint(12.9716, 77.5946);
		Map<String, Object> metadata = new HashMap<>();
		metadata.put("city", "Bengaluru");
		metadata.put("name", "Test Building");
		metadata.put("region", "Karnataka");
		metadata.put("street", "MG Road");
		metadata.put("country", "India");
		metadata.put("district", "Central Bengaluru");
		metadata.put("timezone", null);
		metadata.put("sub_region", "Bangalore Division");
		metadata.put("postal_code", "560001");
		metadata.put("street_number", "123");
		metadata.put("iso_country_code", "IN");
		metadata.put("formatted_address", "123 MG Road, Bengaluru, Karnataka");

		Location location = Location.builder().point(point).name("Test Building").locality(testLocality)
				.metaData(metadata).build();

		Location saved = locationRepository.save(location);
		Location retrieved = locationRepository.findById(saved.getId()).orElseThrow();

		assertNotNull(retrieved.getMetaData());
		assertEquals("Bengaluru", retrieved.getMetaData().get("city"));
		assertEquals("Test Building", retrieved.getMetaData().get("name"));
		assertEquals("Karnataka", retrieved.getMetaData().get("region"));
		assertEquals("MG Road", retrieved.getMetaData().get("street"));
		assertEquals("India", retrieved.getMetaData().get("country"));
		assertEquals("Central Bengaluru", retrieved.getMetaData().get("district"));
		assertEquals("Bangalore Division", retrieved.getMetaData().get("sub_region"));
		assertEquals("560001", retrieved.getMetaData().get("postal_code"));
		assertEquals("123", retrieved.getMetaData().get("street_number"));
		assertEquals("IN", retrieved.getMetaData().get("iso_country_code"));
		assertEquals("123 MG Road, Bengaluru, Karnataka", retrieved.getMetaData().get("formatted_address"));
	}
}
