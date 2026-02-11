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
 * Test GeoNames discovery service.
 *
 * Verifies that the service correctly queries GeoNames API, parses responses,
 * and returns raw discoveries.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("GeoNames Discovery Service")
class GeoNamesDiscoveryServiceTest {

	@Mock
	private RestTemplate restTemplate;

	private GeoNamesDiscoveryService service;

	@BeforeEach
	void setUp() {
		service = new GeoNamesDiscoveryService(restTemplate);
	}

	@Test
	@DisplayName("Should query GeoNames API for Indian cities (PPLA feature code)")
	void discoverCitiesFromGeoNames() {
		// Arrange: Mock GeoNames API response with pagination info
		String mockResponse = """
				{
				  "totalResultsCount": 2,
				  "geonames": [
				    {
				      "geonameId": 1275339,
				      "name": "Bengaluru",
				      "alternateNames": "Bangalore",
				      "adminName1": "Karnataka",
				      "countryCode": "IN",
				      "featureCode": "PPLA"
				    },
				    {
				      "geonameId": 1275714,
				      "name": "Mumbai",
				      "alternateNames": "Bombay",
				      "adminName1": "Maharashtra",
				      "countryCode": "IN",
				      "featureCode": "PPLA"
				    }
				  ]
				}
				""";

		when(restTemplate.getForObject(anyString(), eq(String.class))).thenReturn(mockResponse);

		// Act
		List<RawDiscoveryDTO> discoveries = service.discoverCities("IN");

		// Assert
		assertNotNull(discoveries);
		assertEquals(2, discoveries.size());

		RawDiscoveryDTO first = discoveries.get(0);
		assertEquals("Bengaluru", first.getName());
		assertEquals("Karnataka", first.getState());
		assertEquals("IN", first.getCountryCode());
		assertEquals("CITY", first.getLocalityType());
		assertEquals("GEONAMES", first.getSource());
		assertNotNull(first.getSourceMetadata());
	}

	@Test
	@DisplayName("Should query GeoNames API for towns and districts")
	void discoverTownsAndDistrictsFromGeoNames() {
		String mockTownResponse = """
				{
				  "totalResultsCount": 1,
				  "geonames": [
				    {
				      "geonameId": 2988507,
				      "name": "Aurangabad",
				      "adminName1": "Maharashtra",
				      "countryCode": "IN",
				      "featureCode": "PPLA2"
				    }
				  ]
				}
				""";

		when(restTemplate.getForObject(anyString(), eq(String.class))).thenReturn(mockTownResponse);

		// Act
		List<RawDiscoveryDTO> discoveries = service.discoverTowns("IN");

		// Assert
		assertNotNull(discoveries);
		assertEquals(1, discoveries.size());
		assertEquals("Aurangabad", discoveries.get(0).getName());
		assertEquals("TOWN", discoveries.get(0).getLocalityType());
	}

	@Test
	@DisplayName("Should handle API response with alternate names")
	void parseAlternateNames() {
		String mockResponse = """
				{
				  "totalResultsCount": 1,
				  "geonames": [
				    {
				      "geonameId": 1275339,
				      "name": "Bengaluru",
				      "alternateNames": "Bangalore,Bangaluru,Bengalooru",
				      "adminName1": "Karnataka",
				      "countryCode": "IN",
				      "featureCode": "PPLA"
				    }
				  ]
				}
				""";

		when(restTemplate.getForObject(contains("featureCode=PPLA"), eq(String.class))).thenReturn(mockResponse);

		// Act
		List<RawDiscoveryDTO> discoveries = service.discoverCities("IN");

		// Assert
		assertEquals(1, discoveries.size());
		String sourceMetadata = discoveries.get(0).getSourceMetadata();
		assertNotNull(sourceMetadata);
		assertTrue(sourceMetadata.contains("Bangalore"));
	}

	@Test
	@DisplayName("Should handle empty API response gracefully")
	void handleEmptyResponse() {
		String emptyResponse = """
				{
				  "geonames": []
				}
				""";

		when(restTemplate.getForObject(any(), eq(String.class))).thenReturn(emptyResponse);

		// Act
		List<RawDiscoveryDTO> discoveries = service.discoverCities("IN");

		// Assert
		assertNotNull(discoveries);
		assertTrue(discoveries.isEmpty());
	}

	@Test
	@DisplayName("Should handle API error and return empty list")
	void handleAPIError() {
		when(restTemplate.getForObject(any(), eq(String.class))).thenThrow(new RuntimeException("API timeout"));

		// Act
		List<RawDiscoveryDTO> discoveries = service.discoverCities("IN");

		// Assert
		assertNotNull(discoveries);
		assertTrue(discoveries.isEmpty());
	}
}
