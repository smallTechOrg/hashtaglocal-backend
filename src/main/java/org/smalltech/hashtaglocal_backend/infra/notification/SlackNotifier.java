package org.smalltech.hashtaglocal_backend.infra.notification;

import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.smalltech.hashtaglocal_backend.config.CustomProperties;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

/**
 * Posts simple text alerts to Slack via a single Incoming Webhook URL. If no webhook is
 * configured, the alert is dropped (logged at debug).
 */
@Component
public class SlackNotifier {

  private static final Logger log = LoggerFactory.getLogger(SlackNotifier.class);

  private final RestTemplate restTemplate;
  private final String webhookUrl;

  public SlackNotifier(RestTemplate restTemplate, CustomProperties.Slack slackProperties) {
    this.restTemplate = restTemplate;
    this.webhookUrl = slackProperties.getWebhookUrl();
  }

  public void send(String text) {
    if (webhookUrl == null || webhookUrl.isBlank()) {
      log.debug("No Slack webhook configured — skipping alert");
      return;
    }
    try {
      restTemplate.postForEntity(webhookUrl, Map.of("text", text), String.class);
    } catch (RestClientException e) {
      log.warn("Slack alert failed: {}", e.getMessage());
    }
  }
}
