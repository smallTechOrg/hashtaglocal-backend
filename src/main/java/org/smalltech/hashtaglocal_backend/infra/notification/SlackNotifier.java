package org.smalltech.hashtaglocal_backend.infra.notification;

import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

/** Posts simple text alerts to a Slack channel via an Incoming Webhook URL. */
@Component
public class SlackNotifier {

  private static final Logger log = LoggerFactory.getLogger(SlackNotifier.class);

  private final RestTemplate restTemplate;
  private final String webhookUrl;

  public SlackNotifier(
      RestTemplate restTemplate, @Value("${slack.issue-webhook-url:}") String webhookUrl) {
    this.restTemplate = restTemplate;
    this.webhookUrl = webhookUrl;
  }

  /** Sends {@code text} to the configured webhook. No-op (logged) if no webhook is configured. */
  public void send(String text) {
    if (webhookUrl == null || webhookUrl.isBlank()) {
      log.debug("Slack webhook not configured; skipping alert");
      return;
    }
    try {
      restTemplate.postForEntity(webhookUrl, Map.of("text", text), String.class);
    } catch (RestClientException e) {
      log.warn("Slack alert failed: {}", e.getMessage());
    }
  }
}
