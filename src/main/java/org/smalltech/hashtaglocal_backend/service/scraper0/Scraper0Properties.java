package org.smalltech.hashtaglocal_backend.service.scraper0;

import lombok.Data;
import lombok.ToString;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * {@code scraper0.*}: how this backend reaches the scraper0 v2 API (event sources and the BBMP
 * portal). An empty base URL switches event ingestion and portal issues off; a base URL without an
 * API key is a configuration error and switches them off too.
 */
@Configuration
@ConfigurationProperties(prefix = "scraper0")
@Data
public class Scraper0Properties {

  /** e.g. {@code https://scraper0-staging-api-….run.app} ({@code SCRAPER0_BASE_URL}). */
  private String baseUrl = "";

  /**
   * Sent as {@code X-API-Key} ({@code SCRAPER0_API_KEY}). Required whenever the base URL is set:
   * scraper0 has no keyless access.
   */
  @ToString.Exclude private String apiKey = "";

  /** Sent as {@code timeout_s} on reads; the HTTP read timeout is this plus 30 s. */
  private int readTimeoutS = 60;

  /** Default {@code freshness_hours} for new event sources. */
  private int freshnessHours = 48;

  /** Base URL and API key are both set: calls can be made. */
  public boolean isConfigured() {
    return hasBaseUrl() && hasApiKey();
  }

  public boolean hasBaseUrl() {
    return baseUrl != null && !baseUrl.isBlank();
  }

  public boolean hasApiKey() {
    return apiKey != null && !apiKey.isBlank();
  }
}
