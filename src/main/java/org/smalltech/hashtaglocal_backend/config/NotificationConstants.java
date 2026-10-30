package org.smalltech.hashtaglocal_backend.config;

/**
 * Values used by more than one push-notification sender, defined once so they can never drift
 * apart. Anything specific to a single sender belongs in that sender, not here.
 */
public final class NotificationConstants {

  private NotificationConstants() {}

  /** FCM rejects multicast calls with more than 500 tokens. */
  public static final int FCM_BATCH_SIZE = 500;

  // FCM data payload keys read by the mobile app and written by several senders.
  public static final String KEY_TYPE = "type";
  public static final String KEY_NOTIFICATION_LOG_ID = "notificationLogId";
}
