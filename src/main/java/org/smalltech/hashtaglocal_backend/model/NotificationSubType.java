package org.smalltech.hashtaglocal_backend.model;

/**
 * Finer classification of a {@link NotificationType}, stored in {@code notification_log.sub_type}.
 */
public enum NotificationSubType {
  CHAT_BULLETIN,
  CHAT_POST,
  /** An issue opened (approved) in a locality — {@code source_ref = ISSUE / issues.id}. */
  CHAT_ISSUE,
  /** An issue resolved in a locality — {@code source_ref = ISSUE / issues.id}. */
  CHAT_ISSUE_RESOLVED,
  BROADCAST_LOCALITY
}
