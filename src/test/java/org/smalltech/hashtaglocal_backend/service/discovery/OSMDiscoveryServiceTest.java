package org.smalltech.hashtaglocal_backend.service.discovery;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.smalltech.hashtaglocal_backend.dto.RawDiscoveryDTO;
import org.springframework.web.client.RestTemplate;

/**
 * Test OpenStreetMap discovery service.
 *
 * Verifies that the service correctly queries OSM Overpass API, parses
 * responses, and returns raw discoveries.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("OpenStreetMap Discovery Service")
class OSMDiscoveryServiceTest {

	@Mock
	private RestTemplate restTemplate;

	private OSMDiscoveryService service;

	@BeforeEach
	void setUp() {
		service = new OSMDiscoveryService(restTemplate);
	}

	@Test
	@DisplayName("Should query OSM Overpass API for Indian cities")
	void discoverCitiesFromOSM() {
		// Arrange: Mock Overpass API response
		String mockResponse = """
				{
				  "elements": [
				    {
				      "type": "node",
				      "id": 1,
				      "lat": 12.9716,
				      "lon": 77.5946,
				      "tags": {
				        "name": "Bengaluru",
				        "admin_level": "4",
				        "place": "city"
				      }
				    },
				    {
				      "type": "node",
				      "id": 2,
				      "lat": 19.0760,
				      "lon": 72.8777,
				      "tags": {
				        "name": "Mumbai",
				        "admin_level": "4",
				        "place": "city"
				      }
				    }
				  ]
				}
				""";

		when(restTemplate.postForObject(anyString(), any(), eq(String.class))).thenReturn(mockResponse);

		// Act
		List<RawDiscoveryDTO> discoveries = service.discoverCities("IN");

		// Assert
		assertNotNull(discoveries);
		assertEquals(2, discoveries.size());

		RawDiscoveryDTO first = discoveries.get(0);
		assertEquals("Bengaluru", first.getName());
		assertEquals("CITY", first.getLocalityType());
		assertEquals("OSM", first.getSource());
		assertNotNull(first.getSourceMetadata());
	}

	@Test
	@DisplayName("Should query OSM for towns (admin_level 6)")
	void discoverTownsFromOSM() {
		String mockResponse = """
				{
				  "elements": [
				    {
				      "type": "node",
				      "id": 1,
				      "lat": 16.1625,
				      "lon": 73.8284,
				      "tags": {
				        "name": "Nashik",
				        "admin_level": "6",
				        "place": "town"
				      }
				    }
				  ]
				}
				""";

		when(restTemplate.postForObject(anyString(), any(), eq(String.class))).thenReturn(mockResponse);

		// Act
		List<RawDiscoveryDTO> discoveries = service.discoverTowns("IN");

		// Assert
		assertEquals(1, discoveries.size());
		assertEquals("Nashik", discoveries.get(0).getName());
		assertEquals("TOWN", discoveries.get(0).getLocalityType());
	}

	@Test
	@DisplayName("Should extract state from OSM tags")
	void extractStateFromOSMTags() {
		String mockResponse = """
				{
				  "elements": [
				    {
				      "type": "way",
				      "id": 1,
				      "tags": {
				        "name": "Pune",
				        "admin_level": "6",
				        "place": "city",
				        "state": "Maharashtra"
				      }
				    }
				  ]
				}
				""";

		when(restTemplate.postForObject(anyString(), any(), eq(String.class))).thenReturn(mockResponse);

		// Act
		List<RawDiscoveryDTO> discoveries = service.discoverTowns("IN");

		// Assert
		assertEquals(1, discoveries.size());
		assertEquals("Maharashtra", discoveries.get(0).getState());
	}

	@Test
	@DisplayName("Should handle empty OSM response")
	void handleEmptyResponse() {
		String emptyResponse = """
				{
				  "elements": []
				}
				""";

		when(restTemplate.postForObject(anyString(), any(), eq(String.class))).thenReturn(emptyResponse);

		// Act
		List<RawDiscoveryDTO> discoveries = service.discoverCities("IN");

		// Assert
		assertNotNull(discoveries);
		assertTrue(discoveries.isEmpty());
	}

	@Test
	@DisplayName("Should handle API error gracefully")
	void handleAPIError() {
		when(restTemplate.postForObject(anyString(), any(), eq(String.class)))
			.thenThrow(new RuntimeException("API timeout"));

		// Act
		List<RawDiscoveryDTO> discoveries = service.discoverCities("IN");

		// Assert
		assertNotNull(discoveries);
		assertTrue(discoveries.isEmpty());
	}
}
