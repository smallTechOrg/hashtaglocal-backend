package org.smalltech.hashtaglocal_backend.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.LinkedHashMap;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.smalltech.hashtaglocal_backend.dto.TrackIssueScrapeResponseDTO;
import org.smalltech.hashtaglocal_backend.service.scraper0.Scraper0Client;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * Looks up a BBMP complaint through scraper0: {@code POST
 * /v2/sites/smartoneblr-com/actions/track_issue/read} with {@code {"input": {"tracking_id"},
 * "auth"}}. The portal login is {@code portalissue.auth.*}.
 */
@Service
@Slf4j
public class PortalIssueScrapeClient {

  static final String SITE_ID = "smartoneblr-com";
  static final String TRACK_ACTION = "track_issue";
  static final int TRACK_TIMEOUT_S = 120;

  private final Scraper0Client scraper0Client;
  private final ObjectMapper objectMapper;
  private final String authUsername;
  private final String authPassword;

  public PortalIssueScrapeClient(
      Scraper0Client scraper0Client,
      ObjectMapper objectMapper,
      @Value("${portalissue.auth.username:placeholder_username}") String authUsername,
      @Value("${portalissue.auth.password:placeholder_password}") String authPassword) {
    this.scraper0Client = scraper0Client;
    this.objectMapper = objectMapper;
    this.authUsername = authUsername;
    this.authPassword = authPassword;
  }

  public boolean isConfigured() {
    return scraper0Client.isConfigured();
  }

  /**
   * @param portal the stored portal ({@code SMARTONEBLR}); scraper0 has one BBMP site
   * @throws org.smalltech.hashtaglocal_backend.service.scraper0.Scraper0Exception when scraper0
   *     fails or the lookup is still running
   */
  public TrackIssueScrapeResponseDTO trackIssue(String portal, String trackingId) {
    if (trackingId == null) {
      throw new IllegalArgumentException("trackingId must not be null for portal: " + portal);
    }

    Map<String, Object> auth = new LinkedHashMap<>();
    auth.put("username", authUsername);
    auth.put("password", authPassword);

    log.debug("Tracking portal issue portal={} trackingId={}", portal, trackingId);
    JsonNode output =
        scraper0Client.lookup(
            SITE_ID, TRACK_ACTION, Map.of("tracking_id", trackingId), auth, TRACK_TIMEOUT_S);

    JsonNode metaData = output.get("meta_data");
    @SuppressWarnings("unchecked")
    Map<String, Object> meta =
        metaData == null || !metaData.isObject()
            ? null
            : objectMapper.convertValue(metaData, Map.class);
    return TrackIssueScrapeResponseDTO.builder()
        .data(
            TrackIssueScrapeResponseDTO.Data.builder()
                .status(output.path("status").asText(null))
                .metaData(meta)
                .build())
        .build();
  }
}
