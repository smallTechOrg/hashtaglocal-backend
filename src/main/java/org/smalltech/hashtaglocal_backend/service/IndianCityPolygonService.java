package org.smalltech.hashtaglocal_backend.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.locationtech.jts.geom.*;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

@Slf4j
@Service
public class IndianCityPolygonService {

	private static final String NOMINATIM_URL = "https://nominatim.openstreetmap.org";
	private static final String OVERPASS_URL = "https://overpass-api.de/api/interpreter";
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
	 * Fetches polygon boundary for a city using Nominatim API
	 */
	public Polygon fetchCityPolygon(String cityName) {
		try {
			log.info("Fetching boundary for city: {}", cityName);

			// Search for the city in India
			String searchQuery = cityName + ", India";
			String encodedQuery = URLEncoder.encode(searchQuery, StandardCharsets.UTF_8);

			// First, get the OSM relation ID
			String searchUrl = String.format("%s/search?q=%s&format=json&polygon_geojson=1&limit=1", NOMINATIM_URL,
					encodedQuery);

			JsonNode response = makeGetRequest(searchUrl);

			if (response == null || !response.isArray() || response.size() == 0) {
				log.warn("No results found for city: {}", cityName);
				return null;
			}

			JsonNode cityData = response.get(0);
			JsonNode geoJson = cityData.get("geojson");

			if (geoJson == null || !geoJson.has("type")) {
				log.warn("No geojson data found for city: {}", cityName);
				return null;
			}

			String geometryType = geoJson.get("type").asText();

			// Handle different geometry types
			Polygon polygon = null;
			if ("Polygon".equals(geometryType)) {
				polygon = parsePolygon(geoJson.get("coordinates"));
			} else if ("MultiPolygon".equals(geometryType)) {
				// For MultiPolygon, take the largest polygon
				polygon = parseLargestPolygonFromMultiPolygon(geoJson.get("coordinates"));
			}

			if (polygon != null) {
				log.info("Successfully fetched boundary for: {}", cityName);
			}

			return polygon;

		} catch (Exception e) {
			log.error("Error fetching boundary for city: {}", cityName, e);
			return null;
		}
	}

	/**
	 * Makes a GET request and returns JSON response
	 */
	private JsonNode makeGetRequest(String url) throws Exception {
		HttpHeaders headers = new HttpHeaders();
		headers.set("User-Agent", "HashtagLocal/1.0");
		headers.set("Accept", "application/json");

		HttpEntity<String> entity = new HttpEntity<>(headers);
		ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.GET, entity, String.class);

		return objectMapper.readTree(response.getBody());
	}

	/**
	 * Parses coordinates from GeoJSON Polygon format
	 */
	private Polygon parsePolygon(JsonNode coordinatesNode) {
		try {
			// GeoJSON Polygon format: [ [[lon, lat], [lon, lat], ...] ]
			// First array is outer ring, rest are holes
			JsonNode outerRing = coordinatesNode.get(0);
			Coordinate[] coords = new Coordinate[outerRing.size()];

			for (int i = 0; i < outerRing.size(); i++) {
				JsonNode point = outerRing.get(i);
				double lon = point.get(0).asDouble();
				double lat = point.get(1).asDouble();
				coords[i] = new Coordinate(lon, lat);
			}

			LinearRing shell = geometryFactory.createLinearRing(coords);

			// Handle holes if present
			LinearRing[] holes = null;
			if (coordinatesNode.size() > 1) {
				holes = new LinearRing[coordinatesNode.size() - 1];
				for (int i = 1; i < coordinatesNode.size(); i++) {
					JsonNode holeRing = coordinatesNode.get(i);
					Coordinate[] holeCoords = new Coordinate[holeRing.size()];
					for (int j = 0; j < holeRing.size(); j++) {
						JsonNode point = holeRing.get(j);
						holeCoords[j] = new Coordinate(point.get(0).asDouble(), point.get(1).asDouble());
					}
					holes[i - 1] = geometryFactory.createLinearRing(holeCoords);
				}
			}

			return geometryFactory.createPolygon(shell, holes);
		} catch (Exception e) {
			log.error("Error parsing polygon", e);
			return null;
		}
	}

	/**
	 * Parses MultiPolygon and returns the largest polygon
	 */
	private Polygon parseLargestPolygonFromMultiPolygon(JsonNode coordinatesNode) {
		try {
			Polygon largestPolygon = null;
			double maxArea = 0;

			for (int i = 0; i < coordinatesNode.size(); i++) {
				Polygon polygon = parsePolygon(coordinatesNode.get(i));
				if (polygon != null) {
					double area = polygon.getArea();
					if (area > maxArea) {
						maxArea = area;
						largestPolygon = polygon;
					}
				}
			}

			return largestPolygon;
		} catch (Exception e) {
			log.error("Error parsing MultiPolygon", e);
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
	 * Sleeps to respect rate limiting (Nominatim requires 1 request per second)
	 */
	public void respectRateLimit() {
		try {
			Thread.sleep(1100); // 1.1 seconds to be safe
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
		}
	}
}
