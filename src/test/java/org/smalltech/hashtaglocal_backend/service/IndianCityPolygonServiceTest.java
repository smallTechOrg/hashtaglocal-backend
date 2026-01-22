package org.smalltech.hashtaglocal_backend.service;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
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
	void testFetchCityPolygon() {
		// This test makes an actual API call - use sparingly
		// Uncomment to test actual fetching
		/*
		 * Polygon polygon = polygonService.fetchCityPolygon("Mumbai");
		 * assertNotNull(polygon); assertTrue(polygon.getArea() > 0);
		 * System.out.println("Mumbai polygon has " + polygon.getCoordinates().length +
		 * " points");
		 */
	}
}
