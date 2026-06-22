package org.smalltech.hashtaglocal_backend.config;

import java.util.HashMap;
import java.util.Map;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

public class CustomProperties {

  @Configuration
  @ConfigurationProperties(prefix = "app")
  @Data
  public static class App {
    private Geo geo = new Geo();

    @Data
    public static class Geo {
      private double verifyRadiusMeters;
    }
  }

  /**
   * Slack Incoming Webhook URLs keyed by alert channel (see {@code SlackChannel}). {@code default}
   * is used when a more specific channel has no webhook configured.
   */
  @Configuration
  @ConfigurationProperties(prefix = "slack")
  @Data
  public static class Slack {
    private Map<String, String> webhooks = new HashMap<>();
  }

  @Configuration
  @ConfigurationProperties(prefix = "google")
  @Data
  public static class Google {
    private Storage storage = new Storage();

    @Data
    public static class Storage {
      private String bucketName;
    }
  }
}
