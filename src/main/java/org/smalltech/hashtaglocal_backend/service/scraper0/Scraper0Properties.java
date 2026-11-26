package org.smalltech.hashtaglocal_backend.service.scraper0;

import lombok.Data;
import lombok.ToString;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * {@code scraper0.*}: how this backend reaches the scraper0 v2 API (event sources and the BBMP
 * portal). An empty base URL switches event ingestion and portal issues off.
 */
@Configuration
@ConfigurationProperties(prefix = "scraper0")
@Data
public class Scraper0Properties {

  /** e.g. {@code https://scraper0-staging-api-….run.app} ({@code SCRAPER0_BASE_URL}). */
  private String baseUrl = "";

  /**
   * Sent as {@code X-API-Key} ({@code SCRAPER0_API_KEY}). Empty sends no header, which only works
   * while scraper0 still allows legacy keyless calls.
   */
  @ToString.Exclude private String apiKey = "";

  /** Sent as {@code timeout_s} on reads; the HTTP read timeout is this plus 30 s. */
  private int readTimeoutS = 60;

  /** Default {@code freshness_hours} for new event sources. */
  private int freshnessHours = 48;

  public boolean isConfigured() {
    return baseUrl != null && !baseUrl.isBlank();
  }

  public boolean hasApiKey() {
    return apiKey != null && !apiKey.isBlank();
  }
}
