package org.smalltech.hashtaglocal_backend.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import org.smalltech.hashtaglocal_backend.dto.TrackIssueScrapeRequestDTO;
import org.smalltech.hashtaglocal_backend.dto.TrackIssueScrapeResponseDTO;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
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
      ObjectMapper objectMapper,
      @Value("${portalissue.scrape-url:}") String scrapeUrl,
      @Value("${portalissue.auth.username:placeholder_username}") String authUsername,
      @Value("${portalissue.auth.password:placeholder_password}") String authPassword) {
    this.restTemplate = buildRestTemplate(objectMapper);
    this.scrapeUrl = scrapeUrl;
    this.authUsername = authUsername;
    this.authPassword = authPassword;
  }

  private RestTemplate buildRestTemplate(ObjectMapper objectMapper) {
    RestTemplate template = new RestTemplate();

    MappingJackson2HttpMessageConverter converter = new MappingJackson2HttpMessageConverter();
    converter.setObjectMapper(objectMapper);

    var converters = new ArrayList<HttpMessageConverter<?>>(template.getMessageConverters());
    converters.removeIf(MappingJackson2HttpMessageConverter.class::isInstance);
    converters.add(0, converter);
    template.setMessageConverters(converters);

    return template;
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
