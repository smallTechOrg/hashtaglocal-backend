package org.smalltech.hashtaglocal_backend.service;

import java.time.Duration;
import lombok.extern.slf4j.Slf4j;
import org.smalltech.hashtaglocal_backend.dto.TrackIssueScrapeRequestDTO;
import org.smalltech.hashtaglocal_backend.dto.TrackIssueScrapeResponseDTO;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

@Service
@Slf4j
public class PortalIssueScrapeClient {

  private final RestTemplate restTemplate;
  private final String scrapeUrl;
  private final String authUsername;
  private final String authPassword;

  public PortalIssueScrapeClient(
      RestTemplateBuilder restTemplateBuilder,
      @Value("${portal-issue-tracking.scrape-url:}") String scrapeUrl,
      @Value("${portal-issue-tracking.auth.username:placeholder_username}") String authUsername,
      @Value("${portal-issue-tracking.auth.password:placeholder_password}") String authPassword,
      @Value("${portal-issue-tracking.http.connect-timeout-ms:5000}") long connectTimeoutMs,
      @Value("${portal-issue-tracking.http.read-timeout-ms:15000}") long readTimeoutMs) {
    this.restTemplate =
        restTemplateBuilder
            .setConnectTimeout(Duration.ofMillis(connectTimeoutMs))
            .setReadTimeout(Duration.ofMillis(readTimeoutMs))
            .build();
    this.scrapeUrl = scrapeUrl;
    this.authUsername = authUsername;
    this.authPassword = authPassword;
  }

  public TrackIssueScrapeResponseDTO trackIssue(String portal, String trackingId) {
    if (scrapeUrl == null || scrapeUrl.isBlank()) {
      throw new IllegalStateException("portal-issue-tracking.scrape-url is not configured");
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
