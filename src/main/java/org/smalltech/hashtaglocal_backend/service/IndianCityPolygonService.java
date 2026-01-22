package org.smalltech.hashtaglocal_backend.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.locationtech.jts.geom.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

@Slf4j
@Service
public class IndianCityPolygonService {

	private static final String GOOGLE_MAPS_GEOCODING_URL = "https://maps.googleapis.com/maps/api/geocode/json";

	@Value("${google.maps.api-key:}")
	private String googleMapsApiKey;

	private final ObjectMapper objectMapper = new ObjectMapper();
	private final GeometryFactory geometryFactory = new GeometryFactory(new PrecisionModel(), 4326);
	private final RestTemplate restTemplate = new RestTemplate();

	// List of major Indian cities
	private static final List<String> INDIAN_CITIES = Arrays.asList(
			// Metro cities
			"Mumbai", "Delhi", "Bengaluru", "Hyderabad", "Ahmedabad", "Chennai", "Kolkata", "Surat", "Pune", "Jaipur",

			// Tier 1 cities
			"Lucknow", "Kanpur", "Nagpur", "Indore", "Thane", "Bhopal", "Visakhapatnam", "Pimpri-Chinchwad", "Patna",
			"Vadodara", "Ghaziabad", "Ludhiana", "Agra", "Nashik", "Faridabad", "Meerut", "Rajkot", "Kalyan-Dombivli",
			"Vasai-Virar", "Varanasi", "Srinagar", "Aurangabad", "Dhanbad", "Amritsar", "Navi Mumbai", "Allahabad",
			"Ranchi", "Howrah", "Coimbatore", "Jabalpur", "Gwalior",

			// Tier 2 cities
			"Vijayawada", "Jodhpur", "Madurai", "Raipur", "Kota", "Chandigarh", "Guwahati", "Solapur", "Hubli-Dharwad",
			"Mysore", "Tiruchirappalli", "Bareilly", "Aligarh", "Moradabad", "Jalandhar", "Bhubaneswar", "Salem",
			"Warangal", "Guntur", "Bhiwandi", "Saharanpur", "Gorakhpur", "Bikaner", "Amravati", "Noida", "Jamshedpur",
			"Bhilai", "Cuttack", "Firozabad", "Kochi", "Nellore",

			// State capitals and important cities
			"Thiruvananthapuram", "Dehradun", "Shimla", "Gangtok", "Imphal", "Aizawl", "Kohima", "Itanagar", "Dispur",
			"Shillong", "Agartala", "Panaji", "Daman", "Silvassa", "Port Blair", "Puducherry",

			// Other major cities
			"Bhavnagar", "Siliguri", "Ujjain", "Ajmer", "Durg-Bhilainagar", "Belgaum", "Mangalore", "Gulbarga",
			"Udaipur", "Jammu", "Jhansi", "Erode", "Vellore", "Tiruppur", "Thiruvannamalai", "Thanjavur", "Tirunelveli",
			"Kollam", "Thrissur", "Kozhikode", "Kannur", "Alappuzha", "Malappuram", "Thoothukudi", "Cuddalore",
			"Dindigul", "Rajahmundry", "Kakinada", "Eluru", "Ongole", "Kurnool", "Vizianagaram", "Anantapur", "Kadapa",
			"Tirupati", "Bellary", "Bijapur", "Davangere", "Shimoga", "Tumkur", "Raichur", "Hospet", "Gadag", "Bidar",
			"Chitradurga", "Udupi", "Hassan", "Chikmagalur", "Mandya", "Bagalkot");

	public List<String> getIndianCities() {
		return INDIAN_CITIES;
	}

	/**
	 * Fetches polygon boundary for a city using Google Maps Geocoding API
	 */
	public Polygon fetchCityPolygon(String cityName) {
		try {
			log.info("Fetching boundary for city: {}", cityName);

			if (googleMapsApiKey == null || googleMapsApiKey.isEmpty()) {
				log.error("Google Maps API key not configured. Please set GOOGLE_MAPS_API_KEY environment variable.");
				return null;
			}

			Polygon polygon = fetchFromGoogleMaps(cityName);
			if (polygon != null) {
				return polygon;
			}

			log.warn("Failed to fetch boundary for city: {}", cityName);
			return null;

		} catch (Exception e) {
			log.error("Error fetching boundary for city: {}", cityName, e);
			return null;
		}
	}

	/**
	 * Fetch city boundary from Google Maps Geocoding API - reliable for Indian
	 * cities
	 */
	private Polygon fetchFromGoogleMaps(String cityName) {
		try {
			String address = cityName + ", India";
			String encodedAddress = URLEncoder.encode(address, StandardCharsets.UTF_8);

			String url = String.format("%s?address=%s&key=%s&components=country:IN", GOOGLE_MAPS_GEOCODING_URL,
					encodedAddress, googleMapsApiKey);

			log.debug("Querying Google Maps for: {}", cityName);

			HttpHeaders headers = new HttpHeaders();
			headers.set("User-Agent", "HashtagLocal/1.0");
			HttpEntity<String> entity = new HttpEntity<>(headers);

			ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.GET, entity, String.class);
			JsonNode jsonResponse = objectMapper.readTree(response.getBody());

			String status = jsonResponse.get("status").asText();
			if (!"OK".equals(status)) {
				log.debug("Google Maps returned status: {} for {}", status, cityName);
				return null;
			}

			JsonNode results = jsonResponse.get("results");
			if (results == null || results.size() == 0) {
				log.debug("No Google Maps results for: {}", cityName);
				return null;
			}

			// Find the best result - prefer "locality" or "administrative_area" types
			JsonNode bestResult = null;
			for (JsonNode result : results) {
				JsonNode types = result.get("types");
				if (types != null) {
					for (JsonNode type : types) {
						String typeStr = type.asText();
						if (typeStr.equals("locality") || typeStr.equals("administrative_area_level_2")
								|| typeStr.equals("administrative_area_level_3")) {
							bestResult = result;
							break;
						}
					}
				}
				if (bestResult == null) {
					bestResult = result; // fallback to first result
				}
			}

			if (bestResult == null) {
				return null;
			}

			JsonNode geometry = bestResult.get("geometry");
			if (geometry == null) {
				return null;
			}

			// Get viewport bounds (this gives us the approximate city boundary)
			JsonNode viewport = geometry.get("viewport");
			if (viewport != null) {
				JsonNode northeast = viewport.get("northeast");
				JsonNode southwest = viewport.get("southwest");

				if (northeast != null && southwest != null) {
					double neLat = northeast.get("lat").asDouble();
					double neLng = northeast.get("lng").asDouble();
					double swLat = southwest.get("lat").asDouble();
					double swLng = southwest.get("lng").asDouble();

					// Create a bounding box polygon
					Coordinate[] coords = new Coordinate[5];
					coords[0] = new Coordinate(swLng, swLat); // SW
					coords[1] = new Coordinate(neLng, swLat); // SE
					coords[2] = new Coordinate(neLng, neLat); // NE
					coords[3] = new Coordinate(swLng, neLat); // NW
					coords[4] = new Coordinate(swLng, swLat); // Close ring

					LinearRing shell = geometryFactory.createLinearRing(coords);
					Polygon polygon = geometryFactory.createPolygon(shell);

					log.info("Successfully fetched boundary for {} from Google Maps", cityName);
					return polygon;
				}
			}

			// Fallback: use bounds if viewport not available
			JsonNode bounds = geometry.get("bounds");
			if (bounds != null) {
				JsonNode northeast = bounds.get("northeast");
				JsonNode southwest = bounds.get("southwest");

				if (northeast != null && southwest != null) {
					double neLat = northeast.get("lat").asDouble();
					double neLng = northeast.get("lng").asDouble();
					double swLat = southwest.get("lat").asDouble();
					double swLng = southwest.get("lng").asDouble();

					Coordinate[] coords = new Coordinate[5];
					coords[0] = new Coordinate(swLng, swLat);
					coords[1] = new Coordinate(neLng, swLat);
					coords[2] = new Coordinate(neLng, neLat);
					coords[3] = new Coordinate(swLng, neLat);
					coords[4] = new Coordinate(swLng, swLat);

					LinearRing shell = geometryFactory.createLinearRing(coords);
					Polygon polygon = geometryFactory.createPolygon(shell);

					log.info("Successfully fetched bounds for {} from Google Maps", cityName);
					return polygon;
				}
			}

			log.debug("No viewport/bounds data in Google Maps response for: {}", cityName);
			return null;

		} catch (Exception e) {
			log.debug("Google Maps API failed for {}: {}", cityName, e.getMessage());
			return null;
		}
	}

	/**
	 * Creates a hashtag from city name
	 */
	public String createHashtag(String cityName) {
		// Remove special characters and spaces, capitalize
		String cleaned = cityName.replaceAll("[^a-zA-Z]", "");
		return "#" + cleaned;
	}

	/**
	 * Sleeps to respect rate limiting (Google Maps allows 50 requests per second on
	 * free tier) Using conservative delay to avoid hitting limits
	 */
	public void respectRateLimit() {
		try {
			Thread.sleep(100); // 0.1 seconds - 10 requests per second
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
		}
	}
}
