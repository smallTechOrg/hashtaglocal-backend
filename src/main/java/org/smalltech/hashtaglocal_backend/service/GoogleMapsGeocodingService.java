package org.smalltech.hashtaglocal_backend.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.smalltech.hashtaglocal_backend.dto.GoogleMapsLocationDTO;
import org.smalltech.hashtaglocal_backend.dto.LocationMetadataDTO;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

/**
 * Service for interacting with Google Maps Geocoding API. Provides reverse
 * geocoding to get detailed location information from coordinates.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class GoogleMapsGeocodingService {

	private final RestTemplate restTemplate;
	private static final ObjectMapper objectMapper = new ObjectMapper();

	@Value("${google.maps.api-key:}")
	private String googleMapsApiKey;

	private static final String REVERSE_GEOCODING_URL = "https://maps.googleapis.com/maps/api/geocode/json?latlng=%s,%s&key=%s";

	/**
	 * Perform reverse geocoding to get location details from coordinates.
	 *
	 * @param latitude
	 *            Latitude of the location
	 * @param longitude
	 *            Longitude of the location
	 * @return LocationMetadataDTO with structured location information
	 */
	public LocationMetadataDTO reverseGeocode(double latitude, double longitude) {
		try {
			String url = String.format(REVERSE_GEOCODING_URL, latitude, longitude, googleMapsApiKey);
			log.debug("Reverse geocoding: lat={}, lng={}", latitude, longitude);

			// Get raw response first to debug
			String rawResponse = restTemplate.getForObject(url, String.class);
			log.debug("Google Maps API raw response: {}", rawResponse);

			GoogleMapsLocationDTO response = objectMapper.readValue(rawResponse, GoogleMapsLocationDTO.class);

			if (response == null) {
				log.warn("Null response from Google Maps API for coordinates: ({}, {})", latitude, longitude);
				return null;
			}

			if (response.getStatus() != null && !"OK".equals(response.getStatus())) {
				log.warn("Google Maps API returned status '{}' for coordinates: ({}, {})", response.getStatus(),
						latitude, longitude);
				return null;
			}

			if (response.getResults() == null || response.getResults().isEmpty()) {
				log.warn("No geocoding results for coordinates: ({}, {}), status: {}", latitude, longitude,
						response.getStatus());
				return null;
			}

			GoogleMapsLocationDTO.Result result = response.getResults().get(0);
			return extractMetadata(result);

		} catch (Exception e) {
			log.error("Error during reverse geocoding for ({}, {}): {}", latitude, longitude, e.getMessage(), e);
			return null;
		}
	}

	/**
	 * Extract structured metadata from Google Maps geocoding result. Ensures all
	 * key fields are populated with fallbacks and includes complete Google data.
	 */
	private LocationMetadataDTO extractMetadata(GoogleMapsLocationDTO.Result result) {
		Map<String, String> components = parseAddressComponents(result.getAddressComponents());

		// Extract with intelligent fallbacks for city
		String city = components.get("locality");
		if (city == null) {
			city = components.get("sublocality_level_1");
		}
		if (city == null) {
			city = components.get("administrative_area_level_2");
		}

		// Extract street with fallback to route
		String street = components.get("route");
		if (street == null) {
			street = components.get("street_address");
		}

		// Extract district/sublocality
		String district = components.get("sublocality_level_1");
		if (district == null) {
			district = components.get("sublocality_level_2");
		}

		String name = extractLocationName(components, street, city, district);

		// Extract additional details
		String neighborhood = components.get("sublocality_level_2");
		if (neighborhood == null) {
			neighborhood = components.get("neighborhood");
		}

		String pointOfInterest = components.get("point_of_interest");
		String premise = components.get("premise");
		String establishment = components.get("establishment");

		return LocationMetadataDTO.builder().city(city).name(name).region(components.get("administrative_area_level_1"))
				.street(street).country(components.get("country")).district(district).timezone(null) // Google Maps
																										// Geocoding API
																										// doesn't
																										// provide
																										// timezone
				.subRegion(components.get("administrative_area_level_2")).postalCode(components.get("postal_code"))
				.streetNumber(components.get("street_number")).isoCountryCode(components.get("country_code"))
				.formattedAddress(buildFormattedAddress(components, result.getFormattedAddress()))
				.neighborhood(neighborhood).pointOfInterest(pointOfInterest).premiseName(premise)
				.establishmentType(establishment).googleMapsData(buildGoogleMapsDataMap(result, components))
				.addressComponents(new HashMap<>(components)).build();
	}

	/**
	 * Parse address components into a map for easy access. Handles multiple types
	 * for each component and captures all relevant data.
	 */
	private Map<String, String> parseAddressComponents(List<GoogleMapsLocationDTO.AddressComponent> addressComponents) {
		Map<String, String> components = new HashMap<>();

		if (addressComponents == null) {
			return components;
		}

		for (GoogleMapsLocationDTO.AddressComponent component : addressComponents) {
			List<String> types = component.getTypes();
			if (types == null || types.isEmpty()) {
				continue;
			}

			String longName = component.getLongName();
			String shortName = component.getShortName();

			// Store all relevant types for this component
			for (String type : types) {
				if (type == null)
					continue;

				// Store the long name for all types
				if (!components.containsKey(type)) {
					components.put(type, longName);
				}

				// Store short name for specific types
				if ("country".equals(type)) {
					components.put("country_code", shortName);
				}
			}
		}

		return components;
	}

	/**
	 * Extract a meaningful location name from components. Format: "street_number +
	 * street - area_name" or just "area_name" if no street. Never include city name
	 * in the result.
	 */
	private String extractLocationName(Map<String, String> components, String street, String city, String district) {
		String streetNumber = components.get("street_number");
		String premise = components.get("premise");
		String pointOfInterest = components.get("point_of_interest");
		String establishment = components.get("establishment");
		String sublocality2 = components.get("sublocality_level_2");
		String sublocality1 = components.get("sublocality_level_1");
		String route = components.get("route");

		// Build street address portion: "street_number + street"
		StringBuilder streetPart = new StringBuilder();

		if (streetNumber != null && !streetNumber.isEmpty()) {
			streetPart.append(streetNumber);
			if (street != null && !street.isEmpty()) {
				streetPart.append(" ");
			}
		}

		if (street != null && !street.isEmpty()) {
			streetPart.append(street);
		}

		// Build area portion: prefer sublocality_level_1 (district), then
		// sublocality_level_2
		String areaPart = sublocality1;
		if (areaPart == null || areaPart.isEmpty()) {
			areaPart = sublocality2;
		}

		// Combine street and area with " - " separator
		if (streetPart.length() > 0 && areaPart != null && !areaPart.isEmpty()) {
			String result = streetPart.toString() + " - " + areaPart;
			// Add premise if it's meaningful and different from area
			if (premise != null && !premise.isEmpty() && !premise.equals(areaPart) && !premise.equals(city)) {
				result = premise + ", " + result;
			}
			return result;
		}

		// If we have street but no area, return just street
		if (streetPart.length() > 0) {
			if (premise != null && !premise.isEmpty() && !premise.equals(city)) {
				return premise + ", " + streetPart.toString();
			}
			return streetPart.toString();
		}

		// No street, return just area
		if (areaPart != null && !areaPart.isEmpty()) {
			// If we have premise, add it to area
			if (premise != null && !premise.isEmpty() && !premise.equals(areaPart) && !premise.equals(city)) {
				return premise + " - " + areaPart;
			}
			return areaPart;
		}

		// Fallback to point of interest or establishment
		if (pointOfInterest != null && !pointOfInterest.isEmpty()) {
			return pointOfInterest;
		}

		if (establishment != null && !establishment.isEmpty()) {
			return establishment;
		}

		if (premise != null && !premise.isEmpty()) {
			return premise;
		}

		// Last resort: city name only
		if (city != null && !city.isEmpty()) {
			return city;
		}

		return "Unknown Location";
	}

	/**
	 * Build a comprehensive formatted address using key components. Ensures address
	 * is always populated with the best available information.
	 */
	private String buildFormattedAddress(Map<String, String> components, String fullFormattedAddress) {
		String streetNumber = components.get("street_number");
		String route = components.get("route");
		String sublocality2 = components.get("sublocality_level_2");
		String sublocality1 = components.get("sublocality_level_1");
		String locality = components.get("locality");
		String region = components.get("administrative_area_level_1");
		String postalCode = components.get("postal_code");

		StringBuilder address = new StringBuilder();

		// Start with street if available
		if (streetNumber != null || route != null) {
			if (streetNumber != null) {
				address.append(streetNumber);
			}
			if (route != null) {
				if (address.length() > 0) {
					address.append(" ");
				}
				address.append(route);
			}
		}

		// Add sublocality level 2 (detailed area)
		if (sublocality2 != null) {
			if (address.length() > 0) {
				address.append(", ");
			}
			address.append(sublocality2);
		}

		// Add sublocality level 1 (district/area)
		if (sublocality1 != null) {
			if (address.length() > 0) {
				address.append(", ");
			}
			address.append(sublocality1);
		}

		// Add city/locality
		if (locality != null) {
			if (address.length() > 0) {
				address.append(", ");
			}
			address.append(locality);
		}

		// Add state/region
		if (region != null) {
			if (address.length() > 0) {
				address.append(", ");
			}
			address.append(region);
		}

		// Add postal code if available
		if (postalCode != null) {
			if (address.length() > 0) {
				address.append(" ");
			}
			address.append(postalCode);
		}

		// If we have a good address, return it; otherwise use Google's formatted
		// address
		if (address.length() > 0) {
			return address.toString();
		}

		return fullFormattedAddress != null ? fullFormattedAddress : "Address not available";
	}

	/**
	 * Build a map containing complete Google Maps response data for reference.
	 */
	private Map<String, Object> buildGoogleMapsDataMap(GoogleMapsLocationDTO.Result result,
			Map<String, String> components) {
		Map<String, Object> googleData = new HashMap<>();

		if (result.getFormattedAddress() != null) {
			googleData.put("formatted_address", result.getFormattedAddress());
		}

		if (result.getPlaceId() != null) {
			googleData.put("place_id", result.getPlaceId());
		}

		if (result.getGeometry() != null && result.getGeometry().getLocation() != null) {
			Map<String, Double> location = new HashMap<>();
			location.put("lat", result.getGeometry().getLocation().getLat());
			location.put("lng", result.getGeometry().getLocation().getLng());
			googleData.put("location", location);
		}

		if (result.getTypes() != null && !result.getTypes().isEmpty()) {
			googleData.put("types", result.getTypes());
		}

		// Add all address component types found
		if (!components.isEmpty()) {
			googleData.put("all_components", new HashMap<>(components));
		}

		return googleData;
	}

	/**
	 * Convert LocationMetadataDTO to Map for storing in JSONB column.
	 */
	public Map<String, Object> metadataToMap(LocationMetadataDTO metadata) {
		try {
			@SuppressWarnings("unchecked")
			Map<String, Object> map = objectMapper.convertValue(metadata, Map.class);
			return map;
		} catch (Exception e) {
			log.error("Error converting metadata to map: {}", e.getMessage());
			return new HashMap<>();
		}
	}
}
