package org.smalltech.hashtaglocal_backend.config;

/**
 * Limits shared by every push-notification sender, defined once so they can never drift apart.
 * Anything specific to a single sender belongs in that sender, not here.
 */
public final class NotificationConstants {

  private NotificationConstants() {}

  /** FCM rejects multicast calls with more than 500 tokens. */
  public static final int FCM_BATCH_SIZE = 500;

  /** Longest notification title we send; matches the {@code notification_log.title} column. */
  public static final int MAX_TITLE_LENGTH = 500;

  /** Longest notification body we send. Longer text is cut at a word boundary with an ellipsis. */
  public static final int MAX_BODY_LENGTH = 180;
}
