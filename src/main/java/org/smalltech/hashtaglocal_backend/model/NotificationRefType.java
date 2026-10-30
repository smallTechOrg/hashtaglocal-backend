package org.smalltech.hashtaglocal_backend.model;

/**
 * What kind of record {@code notification_log.source_ref_id} points to (no FK by design — the ids
 * live in different tables).
 */
public enum NotificationRefType {
  ISSUE_UPDATE,
  BULLETIN
}
