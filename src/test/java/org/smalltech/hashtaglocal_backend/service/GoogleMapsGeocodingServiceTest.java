package org.smalltech.hashtaglocal_backend.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.smalltech.hashtaglocal_backend.dto.GoogleMapsLocationDTO;
import org.smalltech.hashtaglocal_backend.dto.LocationMetadataDTO;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestTemplate;

@ExtendWith(MockitoExtension.class)
@DisplayName("Google Maps Geocoding Service Tests")
class GoogleMapsGeocodingServiceTest {

	@Mock
	private RestTemplate restTemplate;

	@InjectMocks
	private GoogleMapsGeocodingService geocodingService;

	@BeforeEach
	void setUp() {
		ReflectionTestUtils.setField(geocodingService, "googleMapsApiKey", "test-api-key");
	}

	@Test
	@DisplayName("Should successfully reverse geocode coordinates")
	void shouldSuccessfullyReverseGeocode() {
		// Arrange
		double lat = 12.9352;
		double lng = 77.6245;

		GoogleMapsLocationDTO.AddressComponent cityComponent = GoogleMapsLocationDTO.AddressComponent.builder()
				.longName("Bengaluru").shortName("Bengaluru").types(List.of("locality", "political")).build();

		GoogleMapsLocationDTO.AddressComponent stateComponent = GoogleMapsLocationDTO.AddressComponent.builder()
				.longName("Karnataka").shortName("KA").types(List.of("administrative_area_level_1", "political"))
				.build();

		GoogleMapsLocationDTO.AddressComponent countryComponent = GoogleMapsLocationDTO.AddressComponent.builder()
				.longName("India").shortName("IN").types(List.of("country", "political")).build();

		GoogleMapsLocationDTO.AddressComponent streetComponent = GoogleMapsLocationDTO.AddressComponent.builder()
				.longName("Sarjapur - Marathahalli Road").shortName("Sarjapur - Marathahalli Rd")
				.types(List.of("route")).build();

		GoogleMapsLocationDTO.AddressComponent districtComponent = GoogleMapsLocationDTO.AddressComponent.builder()
				.longName("Bellandur").shortName("Bellandur").types(List.of("sublocality_level_1", "sublocality"))
				.build();

		GoogleMapsLocationDTO.AddressComponent postalComponent = GoogleMapsLocationDTO.AddressComponent.builder()
				.longName("560103").shortName("560103").types(List.of("postal_code")).build();

		List<GoogleMapsLocationDTO.AddressComponent> components = List.of(cityComponent, stateComponent,
				countryComponent, streetComponent, districtComponent, postalComponent);

		GoogleMapsLocationDTO.Result result = GoogleMapsLocationDTO.Result.builder()
				.formattedAddress("Sarjapur - Marathahalli Road, Bellandur, Bengaluru, Karnataka 560103, India")
				.addressComponents(components).build();

		GoogleMapsLocationDTO response = GoogleMapsLocationDTO.builder().results(List.of(result)).status("OK").build();
		String jsonResponse;
		try {
			jsonResponse = new ObjectMapper().writeValueAsString(response);
		} catch (Exception e) {
			fail("Failed to serialize mock response: " + e.getMessage());
			return;
		}

		when(restTemplate.getForObject(anyString(), eq(String.class))).thenReturn(jsonResponse);

		// Act
		LocationMetadataDTO metadata = geocodingService.reverseGeocode(lat, lng);

		// Assert
		assertNotNull(metadata);
		assertEquals("Bengaluru", metadata.getCity());
		assertEquals("Karnataka", metadata.getRegion());
		assertEquals("India", metadata.getCountry());
		assertEquals("IN", metadata.getIsoCountryCode());
		assertEquals("Sarjapur - Marathahalli Road", metadata.getStreet());
		assertEquals("Bellandur", metadata.getDistrict());
		assertEquals("560103", metadata.getPostalCode());
		assertTrue(metadata.getFormattedAddress().contains("Bengaluru"));
	}

	@Test
	@DisplayName("Should return null when no results found")
	void shouldReturnNullWhenNoResults() {
		// Arrange
		double lat = 12.9352;
		double lng = 77.6245;

		GoogleMapsLocationDTO response = GoogleMapsLocationDTO.builder().results(new ArrayList<>())
				.status("ZERO_RESULTS").build();
		String jsonResponse;
		try {
			jsonResponse = new ObjectMapper().writeValueAsString(response);
		} catch (Exception e) {
			fail("Failed to serialize mock response: " + e.getMessage());
			return;
		}

		when(restTemplate.getForObject(anyString(), eq(String.class))).thenReturn(jsonResponse);

		// Act
		LocationMetadataDTO metadata = geocodingService.reverseGeocode(lat, lng);

		// Assert
		assertNull(metadata);
	}

	@Test
	@DisplayName("Should handle API error gracefully")
	void shouldHandleAPIError() {
		// Arrange
		double lat = 12.9352;
		double lng = 77.6245;

		when(restTemplate.getForObject(anyString(), eq(String.class))).thenThrow(new RuntimeException("API error"));

		// Act
		LocationMetadataDTO metadata = geocodingService.reverseGeocode(lat, lng);

		// Assert
		assertNull(metadata);
	}

	@Test
	@DisplayName("Should convert metadata to map correctly")
	void shouldConvertMetadataToMap() {
		// Arrange
		LocationMetadataDTO metadata = LocationMetadataDTO.builder().city("Bengaluru").region("Karnataka")
				.country("India").isoCountryCode("IN").formattedAddress("Bengaluru, Karnataka").build();

		// Act
		Map<String, Object> map = geocodingService.metadataToMap(metadata);

		// Assert
		assertNotNull(map);
		assertEquals("Bengaluru", map.get("city"));
		assertEquals("Karnataka", map.get("region"));
		assertEquals("India", map.get("country"));
		assertEquals("IN", map.get("iso_country_code"));
	}
}
