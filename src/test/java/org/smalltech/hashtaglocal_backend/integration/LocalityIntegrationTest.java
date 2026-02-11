package org.smalltech.hashtaglocal_backend.integration;

import static org.hamcrest.Matchers.*;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.Polygon;
import org.smalltech.hashtaglocal_backend.entity.Locality;
import org.smalltech.hashtaglocal_backend.repository.LocalityRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.reactive.server.WebTestClient;

/**
 * Integration tests for Locality API endpoints.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@DisplayName("Locality Integration Tests")
@DirtiesContext(classMode = DirtiesContext.ClassMode.BEFORE_EACH_TEST_METHOD)
class LocalityIntegrationTest {

	private static final String LOCALITIES_POLYGONS_URL = "/api/localities/polygons";

	@Autowired
	private WebTestClient webTestClient;

	@Autowired
	private LocalityRepository localityRepository;

	private GeometryFactory geometryFactory;

	@BeforeEach
	void setup() {
		geometryFactory = new GeometryFactory();
		localityRepository.deleteAll();
	}

	@Test
	@DisplayName("Should return all localities with polygons in GeoJSON format")
	void testGetAllLocalitiesWithPolygons() {
		// Arrange: Create test localities
		Locality locality1 = createAndSaveLocality("test_indiranagar", "Indiranagar", 77.6408, 12.9716, 77.6500,
				12.9800);
		Locality locality2 = createAndSaveLocality("test_koramangala", "Koramangala", 77.6100, 12.9352, 77.6200,
				12.9400);

		// Act & Assert
		webTestClient.get().uri(LOCALITIES_POLYGONS_URL).exchange().expectStatus().isOk().expectBody()
				.jsonPath("$.length()").isEqualTo(2).jsonPath("$[0].id").isNotEmpty().jsonPath("$[0].hashtag")
				.isEqualTo("test_indiranagar").jsonPath("$[0].name").isEqualTo("Indiranagar")
				.jsonPath("$[0].geoBoundary").isNotEmpty().jsonPath("$[0].geoBoundary.type").isEqualTo("Polygon")
				.jsonPath("$[0].geoBoundary.coordinates").isArray().jsonPath("$[0].geoBoundary.coordinates[0]")
				.isArray().jsonPath("$[0].geoBoundary.coordinates[0].length()").value(greaterThanOrEqualTo(4))
				.jsonPath("$[1].hashtag").isEqualTo("test_koramangala").jsonPath("$[1].name").isEqualTo("Koramangala");
	}

	@Test
	@DisplayName("Should return empty array when no localities exist")
	void testGetAllLocalitiesWithPolygons_EmptyDatabase() {
		// Act & Assert
		webTestClient.get().uri(LOCALITIES_POLYGONS_URL).exchange().expectStatus().isOk().expectBody()
				.jsonPath("$.length()").isEqualTo(0).jsonPath("$").isArray();
	}

	@Test
	@DisplayName("Should return correct GeoJSON coordinate format [lng, lat]")
	void testGeoJSONCoordinateFormat() {
		// Arrange
		createAndSaveLocality("test_geojson", "Test Locality", 77.6408, 12.9716, 77.6500, 12.9800);

		// Act & Assert
		webTestClient.get().uri(LOCALITIES_POLYGONS_URL).exchange().expectStatus().isOk().expectBody()
				.jsonPath("$[0].geoBoundary.coordinates[0][0][0]").isNumber() // longitude
				.jsonPath("$[0].geoBoundary.coordinates[0][0][1]").isNumber() // latitude
				.jsonPath("$[0].geoBoundary.coordinates[0][0][0]")
				.value(allOf(greaterThanOrEqualTo(-180.0), lessThanOrEqualTo(180.0)))
				.jsonPath("$[0].geoBoundary.coordinates[0][0][1]")
				.value(allOf(greaterThanOrEqualTo(-90.0), lessThanOrEqualTo(90.0)));
	}

	@Test
	@DisplayName("Should return multiple localities correctly")
	void testGetMultipleLocalities() {
		// Arrange: Create 5 test localities
		for (int i = 1; i <= 5; i++) {
			createAndSaveLocality("test_locality" + i, "Locality " + i, 77.6 + i * 0.01, 12.9 + i * 0.01,
					77.6 + i * 0.01 + 0.01, 12.9 + i * 0.01 + 0.01);
		}

		// Act & Assert
		webTestClient.get().uri(LOCALITIES_POLYGONS_URL).exchange().expectStatus().isOk().expectBody()
				.jsonPath("$.length()").isEqualTo(5).jsonPath("$[*].hashtag").value(hasItems("test_locality1",
						"test_locality2", "test_locality3", "test_locality4", "test_locality5"))
				.jsonPath("$[*].geoBoundary.type").value(everyItem(equalTo("Polygon")));
	}

	/**
	 * Helper method to create and save a test locality with polygon.
	 */
	private Locality createAndSaveLocality(String hashtag, String name, double minLng, double minLat, double maxLng,
			double maxLat) {
		Coordinate[] coordinates = new Coordinate[]{new Coordinate(minLng, minLat), new Coordinate(maxLng, minLat),
				new Coordinate(maxLng, maxLat), new Coordinate(minLng, maxLat), new Coordinate(minLng, minLat) // Close
																												// the
																												// ring
		};
		Polygon polygon = geometryFactory.createPolygon(coordinates);

		Locality locality = Locality.builder().hashtag(hashtag).name(name).geoBoundary(polygon).build();

		return localityRepository.save(locality);
	}
}
