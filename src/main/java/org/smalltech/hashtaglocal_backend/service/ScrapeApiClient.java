package org.smalltech.hashtaglocal_backend.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.smalltech.hashtaglocal_backend.dto.ScrapeEventDTO;
import org.smalltech.hashtaglocal_backend.dto.ScrapeRequestDTO;
import org.smalltech.hashtaglocal_backend.dto.ScrapeResponseDTO;
import org.smalltech.hashtaglocal_backend.model.EventPortalModel;
import org.smalltech.hashtaglocal_backend.model.ScrapeActionType;
import org.smalltech.hashtaglocal_backend.model.ScrapeSource;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ResourceLoader;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

/**
 * Fetches raw event data either from an HTTP scrape service or a local seed file.
 *
 * <ul>
 *   <li>If {@code events.scrape.url} is set → iterates all {@link EventPortalModel} values where
 *       {@link EventPortalModel#supportsFetchEvents()} is true, POSTing once per portal.
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
    List<ScrapeEventDTO> all = new ArrayList<>();
    for (EventPortalModel portal : EventPortalModel.values()) {
      if (portal.supportsFetchEvents()) {
        all.addAll(
            fetchForPortal(portal, ScrapeSource.EVENT_PORTAL, ScrapeActionType.FETCH_EVENTS));
      }
    }
    return all;
  }

  private List<ScrapeEventDTO> fetchForPortal(
      EventPortalModel portal, ScrapeSource source, ScrapeActionType actionType) {
    try {
      log.debug("Fetching events for portal {} from {}", portal, scrapeUrl);

      ScrapeRequestDTO body =
          ScrapeRequestDTO.of(
              source,
              actionType,
              portal.name(),
              Map.of(
                  "event_filter", portal.getEventFilter(),
                  "category_filter", portal.getCategoryFilter()));

      HttpHeaders headers = new HttpHeaders();
      headers.setContentType(MediaType.APPLICATION_JSON);
      HttpEntity<ScrapeRequestDTO> request = new HttpEntity<>(body, headers);

      ScrapeResponseDTO response =
          restTemplate.postForObject(scrapeUrl, request, ScrapeResponseDTO.class);
      if (response == null || response.getData() == null) {
        log.warn("Scrape service returned null or empty response for portal {}", portal);
        return Collections.emptyList();
      }
      List<ScrapeEventDTO> events = response.getData().getEvents();
      log.info("Fetched {} raw events for portal {}", events == null ? 0 : events.size(), portal);
      return events != null ? events : Collections.emptyList();
    } catch (Exception e) {
      log.error("Failed to fetch events for portal {}: {}", portal, e.getMessage());
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
