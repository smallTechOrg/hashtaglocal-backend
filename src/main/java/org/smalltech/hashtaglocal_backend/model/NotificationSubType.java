package org.smalltech.hashtaglocal_backend.model;

/**
 * Finer classification of a {@link NotificationType}, stored in {@code notification_log.sub_type}.
 */
public enum NotificationSubType {
  CHAT_BULLETIN,
  CHAT_POST,
  CHAT_ISSUE,
  BROADCAST_LOCALITY
}
