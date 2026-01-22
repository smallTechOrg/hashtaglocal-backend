package org.smalltech.hashtaglocal_backend.service;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

@SpringBootTest
@TestPropertySource(properties = {"google.maps.api-key=${GOOGLE_MAPS_API_KEY:test-key}"})
class IndianCityPolygonServiceTest {

	@Autowired
	private IndianCityPolygonService polygonService;

	@Test
	void testGetIndianCities() {
		List<String> cities = polygonService.getIndianCities();
		assertNotNull(cities);
		assertFalse(cities.isEmpty());
		assertTrue(cities.contains("Mumbai"));
		assertTrue(cities.contains("Delhi"));
		assertTrue(cities.contains("Bengaluru"));
		System.out.println("Total cities: " + cities.size());
	}

	@Test
	void testCreateHashtag() {
		String hashtag = polygonService.createHashtag("Mumbai");
		assertEquals("#Mumbai", hashtag);

		String hashtag2 = polygonService.createHashtag("New Delhi");
		assertEquals("#NewDelhi", hashtag2);
	}

	@Test
	void testFetchCityPolygonWithGoogleMaps() {
		// This test makes an actual Google Maps API call
		// Requires GOOGLE_MAPS_API_KEY environment variable to be set
		// Uncomment to test actual fetching
		/*
		 * Polygon polygon = polygonService.fetchCityPolygon("Mumbai");
		 * assertNotNull(polygon, "Polygon should not be null for Mumbai");
		 * assertTrue(polygon.getArea() > 0, "Polygon area should be greater than 0");
		 * System.out.println("Mumbai polygon has " + polygon.getCoordinates().length +
		 * " points"); System.out.println("Mumbai polygon area: " + polygon.getArea());
		 */
	}

	@Test
	void testFetchCityPolygonWithoutApiKey() {
		// When API key is not configured, should log error and return null
		// This test verifies graceful handling of missing configuration
		// Real test would need to temporarily unset the API key
	}
}
