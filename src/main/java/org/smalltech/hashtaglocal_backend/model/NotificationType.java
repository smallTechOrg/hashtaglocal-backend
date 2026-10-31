package org.smalltech.hashtaglocal_backend.model;

public enum NotificationType {
  ISSUE_DETAIL,
  BROADCAST,
  CHAT,
  /** System-generated chat notifications; the specific kind is in {@link NotificationSubType}. */
  AUTO_CHAT
}
