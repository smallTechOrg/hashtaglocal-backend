package org.smalltech.hashtaglocal_backend.service.discovery;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.smalltech.hashtaglocal_backend.dto.RawDiscoveryDTO;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

/**
 * Queries GeoNames API for Indian cities, towns, and districts with pagination.
 *
 * <p>Feature codes: - PPLA: State capital (cities) - PPLA2: District capital (towns) - ADM2:
 * Administrative division level 2 (districts)
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class GeoNamesDiscoveryService {

  private final RestTemplate restTemplate;

  @Value("${geonames.username:demo}")
  private String geonamesUsername;

  private static final String GEONAMES_API_URL = "http://api.geonames.org/searchJSON";
  private static final int MAX_ROWS_PER_REQUEST = 1000;
  private static final int TOTAL_RECORDS_LIMIT = 10000;
  private static final ObjectMapper objectMapper = new ObjectMapper();

  /** Query GeoNames for Indian state capitals (cities) - all pages. */
  public List<RawDiscoveryDTO> discoverCities(String countryCode) {
    return fetchAllPages(countryCode, "PPLA", "CITY");
  }

  /** Query GeoNames for Indian district capitals (towns) - all pages. */
  public List<RawDiscoveryDTO> discoverTowns(String countryCode) {
    return fetchAllPages(countryCode, "PPLA2", "TOWN");
  }

  /** Query GeoNames for Indian administrative divisions (districts) - all pages. */
  public List<RawDiscoveryDTO> discoverDistricts(String countryCode) {
    return fetchAllPages(countryCode, "ADM2", "DISTRICT");
  }

  private List<RawDiscoveryDTO> fetchAllPages(
      String countryCode, String featureCode, String localityType) {
    List<RawDiscoveryDTO> discoveries = new ArrayList<>();
    int startRow = 0;

    try {
      while (startRow < TOTAL_RECORDS_LIMIT) {
        log.info("📥 Fetching {} [{}] starting at row {}", localityType, featureCode, startRow);

        String url =
            String.format(
                "%s?featureCode=%s&country=%s&maxRows=%d&startRow=%d&username=%s",
                GEONAMES_API_URL,
                featureCode,
                countryCode,
                MAX_ROWS_PER_REQUEST,
                startRow,
                geonamesUsername);

        String response = restTemplate.getForObject(url, String.class);
        JsonNode rootNode = objectMapper.readTree(response);

        int totalResults = rootNode.get("totalResultsCount").asInt(0);
        JsonNode geonamesArray = rootNode.get("geonames");

        if (geonamesArray == null || !geonamesArray.isArray() || geonamesArray.size() == 0) {
          log.info("✅ {} fetch complete. Total records: {}", localityType, discoveries.size());
          break;
        }

        geonamesArray.forEach(
            node -> {
              RawDiscoveryDTO dto =
                  RawDiscoveryDTO.builder()
                      .name(node.get("name").asText())
                      .state(node.get("adminName1").asText())
                      .countryCode(countryCode)
                      .localityType(localityType)
                      .source("GEONAMES")
                      .sourceMetadata(buildSourceMetadata(node))
                      .build();
              discoveries.add(dto);
            });

        log.info(
            "📊 Batch fetched {} records (total so far: {}/{})",
            geonamesArray.size(),
            discoveries.size(),
            totalResults);

        if (discoveries.size() >= totalResults) {
          log.info("✅ {} discovery complete: {} records", localityType, discoveries.size());
          break;
        }

        startRow += MAX_ROWS_PER_REQUEST;
      }

    } catch (Exception e) {
      log.error("❌ Error querying GeoNames for {}: {}", localityType, e.getMessage());
    }

    log.info("📈 {} Final Count: {}", localityType, discoveries.size());
    return discoveries;
  }

  private String buildSourceMetadata(JsonNode node) {
    try {
      return objectMapper.writeValueAsString(
          new Object() {
            public final String geonameId = node.get("geonameId").asText();
            public final String featureCode = node.get("featureCode").asText();
            public final String alternateNames = node.get("alternateNames").asText();
          });
    } catch (Exception e) {
      log.warn("Failed to build source metadata: {}", e.getMessage());
      return "{}";
    }
  }
}
