package org.smalltech.hashtaglocal_backend.config;

/**
 * Values shared by every push-notification sender (broadcast, issue updates, bulletin, ...). Keep
 * anything that is part of the backend ↔ mobile contract (payload keys) or reused by more than one
 * sender here, so it is defined once.
 */
public final class NotificationConstants {

  private NotificationConstants() {}

  // ---- FCM ----

  /** FCM rejects multicast calls with more than 500 tokens. */
  public static final int FCM_BATCH_SIZE = 500;

  /** Analytics label suffix for pushes triggered by an admin (label = type + suffix). */
  public static final String ANALYTICS_SUFFIX_ADMIN = "_admin";

  /** Analytics label suffix for pushes triggered by the system (label = type + suffix). */
  public static final String ANALYTICS_SUFFIX_SYSTEM = "_system";

  // ---- FCM data payload keys (read by the mobile app) ----

  public static final String KEY_TYPE = "type";
  public static final String KEY_NOTIFICATION_LOG_ID = "notificationLogId";
  public static final String KEY_ISSUE_ID = "issueId";
  public static final String KEY_STATUS = "status";
  public static final String KEY_EVENT = "event";
  public static final String KEY_BULLETIN_ID = "bulletinId";
  public static final String KEY_LOCALITY_ID = "localityId";
  public static final String KEY_DATE = "date";

  // ---- Bulletin notification ----

  /**
   * Value of the FCM {@code type} key the mobile app routes on. Deliberately not the log's {@code
   * NotificationType} (AUTO_CHAT), which is a backend classification.
   */
  public static final String PAYLOAD_TYPE_BULLETIN = "BULLETIN";

  public static final String BULLETIN_ANALYTICS_LABEL = "bulletin" + ANALYTICS_SUFFIX_SYSTEM;

  public static final String BULLETIN_TITLE_PREFIX = "Daily Bulletin — ";
  public static final String BULLETIN_FALLBACK_BODY = "Today's weather update and quiz are ready.";
  public static final int BULLETIN_MAX_BODY_LENGTH = 180;
}
