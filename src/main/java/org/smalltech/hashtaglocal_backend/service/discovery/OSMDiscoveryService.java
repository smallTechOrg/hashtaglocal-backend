package org.smalltech.hashtaglocal_backend.service.discovery;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.smalltech.hashtaglocal_backend.dto.RawDiscoveryDTO;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;

/**
 * Queries OpenStreetMap Overpass API for Indian cities, towns, and districts.
 * With generous timeout (60s) for large queries.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class OSMDiscoveryService {

	private final RestTemplate restTemplate;

	private static final String OVERPASS_API_URL = "https://overpass-api.de/api/interpreter";
	private static final int TIMEOUT_SECONDS = 60;
	private static final ObjectMapper objectMapper = new ObjectMapper();

	/**
	 * Query OSM for Indian cities using place=city tag.
	 */
	public List<RawDiscoveryDTO> discoverCities(String countryCode) {
		if (!countryCode.equals("IN")) {
			return new ArrayList<>();
		}
		return queryOverpassWithRetry("[out:json][timeout:60];" + "area[\"ISO3166-1\"=\"IN\"][admin_level=2];"
				+ "(node[place=city](area);" + "way[place=city](area);" + "rel[place=city](area););" + "out center;",
				"CITY", 3);
	}

	/**
	 * Query OSM for Indian towns using place=town tag.
	 */
	public List<RawDiscoveryDTO> discoverTowns(String countryCode) {
		if (!countryCode.equals("IN")) {
			return new ArrayList<>();
		}
		return queryOverpassWithRetry("[out:json][timeout:60];" + "area[\"ISO3166-1\"=\"IN\"][admin_level=2];"
				+ "(node[place=town](area);" + "way[place=town](area);" + "rel[place=town](area););" + "out center;",
				"TOWN", 3);
	}

	/**
	 * Query OSM for Indian districts (admin_level 5).
	 */
	public List<RawDiscoveryDTO> discoverDistricts(String countryCode) {
		if (!countryCode.equals("IN")) {
			return new ArrayList<>();
		}
		return queryOverpassWithRetry("[out:json][timeout:60];" + "area[\"ISO3166-1\"=\"IN\"][admin_level=2];"
				+ "(relation[boundary=administrative][admin_level=5](area););" + "out center;", "DISTRICT", 3);
	}

	private List<RawDiscoveryDTO> queryOverpassWithRetry(String query, String localityType, int maxRetries) {
		int retries = 0;
		while (retries < maxRetries) {
			try {
				return queryOverpass(query, localityType);
			} catch (Exception e) {
				retries++;
				if (retries < maxRetries) {
					long waitTime = 5000L * retries; // Exponential backoff: 5s, 10s, 15s
					log.warn("⏳ OSM query timeout for {} (attempt {}/{}). Retrying in {}ms...", localityType, retries,
							maxRetries, waitTime);
					try {
						Thread.sleep(waitTime);
					} catch (InterruptedException ie) {
						Thread.currentThread().interrupt();
						log.error("❌ OSM {} query interrupted", localityType);
						return new ArrayList<>();
					}
				} else {
					log.error("❌ OSM {} query failed after {} retries: {}", localityType, maxRetries, e.getMessage());
					return new ArrayList<>();
				}
			}
		}
		return new ArrayList<>();
	}

	private List<RawDiscoveryDTO> queryOverpass(String query, String localityType) {
		List<RawDiscoveryDTO> discoveries = new ArrayList<>();

		try {
			log.info("📡 Querying OSM Overpass API for {}", localityType);

			HttpHeaders headers = new HttpHeaders();
			headers.setContentType(MediaType.TEXT_PLAIN);
			HttpEntity<String> request = new HttpEntity<>(query, headers);

			long startTime = System.currentTimeMillis();
			String response = restTemplate.postForObject(OVERPASS_API_URL, request, String.class);
			long duration = System.currentTimeMillis() - startTime;

			log.info("✅ OSM API response received in {}ms", duration);

			JsonNode rootNode = objectMapper.readTree(response);
			JsonNode elements = rootNode.get("elements");

			if (elements != null && elements.isArray()) {
				elements.forEach(element -> {
					String name = extractName(element);
					String state = extractState(element);

					if (name != null && !name.isEmpty() && !name.equals("India")) {
						RawDiscoveryDTO dto = RawDiscoveryDTO.builder().name(name)
								.state(state != null ? state : "Unknown").countryCode("IN").localityType(localityType)
								.source("OSM").sourceMetadata(buildSourceMetadata(element)).build();
						discoveries.add(dto);
					}
				});

				log.info("📊 Found {} {} from OSM", discoveries.size(), localityType);
			}

		} catch (ResourceAccessException e) {
			log.warn("⏳ OSM API timeout or network error: {}", e.getMessage());
			throw e;
		} catch (Exception e) {
			log.error("❌ Error parsing OSM response: {}", e.getMessage());
		}

		return discoveries;
	}

	private String extractName(JsonNode element) {
		JsonNode tags = element.get("tags");
		if (tags != null) {
			JsonNode name = tags.get("name");

			if (name != null) {
				return name.asText();
			}
		}
		return null;
	}

	private String extractState(JsonNode element) {
		JsonNode tags = element.get("tags");
		if (tags != null) {
			// Try common state field names in OSM
			JsonNode state = tags.get("state");
			if (state != null) {
				return state.asText();
			}
			JsonNode adminLevel = tags.get("admin_level");
			if (adminLevel != null && adminLevel.asText().equals("4")) {
				// State level admin boundary, use its name
				return extractName(element);
			}
		}
		return null;
	}

	private String buildSourceMetadata(JsonNode element) {
		try {
			JsonNode tags = element.get("tags");
			return objectMapper.writeValueAsString(new Object() {
				public final String osmId = element.get("id").asText();
				public final String elementType = element.get("type").asText();
				public final String adminLevel = tags != null ? tags.get("admin_level").asText() : "N/A";
			});
		} catch (Exception e) {
			log.warn("Failed to build source metadata: {}", e.getMessage());
			return "{}";
		}
	}
}
