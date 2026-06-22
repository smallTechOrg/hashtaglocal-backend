package org.smalltech.hashtaglocal_backend.infra.notification;

import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.smalltech.hashtaglocal_backend.config.CustomProperties;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

/**
 * Posts simple text alerts to Slack via Incoming Webhook URLs, one per {@link SlackChannel}. A
 * channel with no webhook configured falls back to {@code slack.webhooks.default}; if that's also
 * unset, the alert is dropped (logged at debug).
 */
@Component
public class SlackNotifier {

  private static final Logger log = LoggerFactory.getLogger(SlackNotifier.class);
  private static final String DEFAULT_KEY = "default";

  private final RestTemplate restTemplate;
  private final Map<String, String> webhooks;

  public SlackNotifier(RestTemplate restTemplate, CustomProperties.Slack slackProperties) {
    this.restTemplate = restTemplate;
    this.webhooks = slackProperties.getWebhooks();
  }

  public void send(SlackChannel channel, String text) {
    String url = webhooks.get(channel.key());
    if (url == null || url.isBlank()) {
      url = webhooks.get(DEFAULT_KEY);
    }
    if (url == null || url.isBlank()) {
      log.debug("No Slack webhook configured for channel '{}' — skipping alert", channel.key());
      return;
    }
    try {
      restTemplate.postForEntity(url, Map.of("text", text), String.class);
    } catch (RestClientException e) {
      log.warn("Slack alert to '{}' failed: {}", channel.key(), e.getMessage());
    }
  }
}
