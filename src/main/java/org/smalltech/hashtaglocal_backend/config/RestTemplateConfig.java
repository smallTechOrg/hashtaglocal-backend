package org.smalltech.hashtaglocal_backend.config;

import java.time.Duration;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;

/**
 * Configuration for RestTemplate bean used by discovery and import services. Configured with
 * generous timeouts for long-running queries (e.g., OSM Overpass API).
 */
@Configuration
public class RestTemplateConfig {

  @Bean
  public RestTemplate restTemplate(RestTemplateBuilder builder) {
    return builder
        .requestFactory(
            () -> {
              SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
              requestFactory.setConnectTimeout(Duration.ofSeconds(10));
              requestFactory.setReadTimeout(Duration.ofSeconds(90));
              return requestFactory;
            })
        .build();
  }
}
