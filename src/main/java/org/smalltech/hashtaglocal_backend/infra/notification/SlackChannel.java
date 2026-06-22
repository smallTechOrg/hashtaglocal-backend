package org.smalltech.hashtaglocal_backend.infra.notification;

import java.util.Locale;

/**
 * Named Slack alert channels. Each maps to a {@code slack.webhooks.<key>} entry; falls back to
 * {@code slack.webhooks.default} if its own webhook isn't configured.
 */
public enum SlackChannel {
  REVIEW,
  CRON,
  ACCOUNT_DELETION;

  public String key() {
    return name().toLowerCase(Locale.ROOT);
  }
}
