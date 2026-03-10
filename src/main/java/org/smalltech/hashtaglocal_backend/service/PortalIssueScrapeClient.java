package org.smalltech.hashtaglocal_backend.service;

import org.smalltech.hashtaglocal_backend.dto.TrackIssueScrapeRequestDTO;
import org.smalltech.hashtaglocal_backend.dto.TrackIssueScrapeResponseDTO;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import lombok.extern.slf4j.Slf4j;

@Service
@Slf4j
public class PortalIssueScrapeClient {

  private final RestTemplate restTemplate;
  private final String scrapeUrl;
  private final String authUsername;
  private final String authPassword;

  public PortalIssueScrapeClient(
      @Value("${portalissue.scrape-url:}") String scrapeUrl,
      @Value("${portalissue.auth.username:placeholder_username}") String authUsername,
      @Value("${portalissue.auth.password:placeholder_password}") String authPassword) {
    this.restTemplate = new RestTemplate();
    this.scrapeUrl = scrapeUrl;
    this.authUsername = authUsername;
    this.authPassword = authPassword;
  }

  public TrackIssueScrapeResponseDTO trackIssue(String portal, String trackingId) {
    if (scrapeUrl == null || scrapeUrl.isBlank()) {
      throw new IllegalStateException("portalissue.scrape-url is not configured");
    }

    TrackIssueScrapeRequestDTO request =
        TrackIssueScrapeRequestDTO.of(portal, trackingId, authUsername, authPassword);

    log.debug("Calling portal scrape API for portal={} trackingId={}", portal, trackingId);
    TrackIssueScrapeResponseDTO response =
        restTemplate.postForObject(scrapeUrl, request, TrackIssueScrapeResponseDTO.class);

    if (response == null || response.getData() == null) {
      throw new IllegalStateException("Portal scrape API returned empty response");
    }

    return response;
  }
}
