package org.smalltech.hashtaglocal_backend.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Collections;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.smalltech.hashtaglocal_backend.dto.ScrapeEventDTO;
import org.smalltech.hashtaglocal_backend.dto.ScrapeResponseDTO;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

/**
 * Fetches raw event data either from an HTTP scrape service or a local seed file.
 *
 * <ul>
 *   <li>If {@code events.scrape.url} is set → calls that URL (HTTP GET).
 *   <li>Otherwise → reads from the file at {@code events.scrape.seed-file} (default: {@code
 *       classpath:events-seed.json}). Edit that file to supply test data manually.
 * </ul>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ScrapeApiClient {

  private final RestTemplate restTemplate;
  private final ObjectMapper objectMapper;
  private final ResourceLoader resourceLoader;

  @Value("${events.scrape.url:}")
  private String scrapeUrl;

  @Value("${events.scrape.seed-file:classpath:events-seed.json}")
  private String seedFile;

  public List<ScrapeEventDTO> fetchEvents() {
    if (scrapeUrl != null && !scrapeUrl.isBlank()) {
      return fetchFromUrl();
    }
    return readFromSeedFile();
  }

  private List<ScrapeEventDTO> fetchFromUrl() {
    try {
      log.debug("Fetching events from scrape service: {}", scrapeUrl);
      ScrapeResponseDTO response = restTemplate.getForObject(scrapeUrl, ScrapeResponseDTO.class);
      if (response == null || response.getData() == null) {
        log.warn("Scrape service returned null or empty response");
        return Collections.emptyList();
      }
      List<ScrapeEventDTO> events = response.getData().getEvents();
      log.info("Fetched {} raw events from scrape service", events == null ? 0 : events.size());
      return events != null ? events : Collections.emptyList();
    } catch (Exception e) {
      log.error("Failed to fetch events from scrape service: {}", e.getMessage());
      return Collections.emptyList();
    }
  }

  private List<ScrapeEventDTO> readFromSeedFile() {
    try {
      var resource = resourceLoader.getResource(seedFile);
      if (!resource.exists()) {
        log.warn(
            "No scrape URL configured and seed file not found at '{}' — no events to ingest",
            seedFile);
        return Collections.emptyList();
      }
      ScrapeResponseDTO response =
          objectMapper.readValue(resource.getInputStream(), ScrapeResponseDTO.class);
      if (response == null || response.getData() == null) {
        log.warn("Seed file '{}' is empty or malformed", seedFile);
        return Collections.emptyList();
      }
      List<ScrapeEventDTO> events = response.getData().getEvents();
      log.info(
          "Loaded {} events from seed file '{}'", events == null ? 0 : events.size(), seedFile);
      return events != null ? events : Collections.emptyList();
    } catch (Exception e) {
      log.error("Failed to read seed file '{}': {}", seedFile, e.getMessage());
      return Collections.emptyList();
    }
  }
}
