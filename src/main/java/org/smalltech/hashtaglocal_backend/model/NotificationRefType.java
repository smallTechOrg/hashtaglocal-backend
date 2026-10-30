package org.smalltech.hashtaglocal_backend.model;

/**
 * Which table {@code notification_log.source_ref_id} is an id in (no FK by design — the ids live in
 * different tables). Each constant is the entity for one table, so {@code source_ref_type =
 * BULLETIN} with {@code source_ref_id = 7} means {@code bulletins.id = 7}.
 */
public enum NotificationRefType {
  /** {@code bulletins.id} */
  BULLETIN("bulletins"),
  /** {@code issues.id} */
  ISSUE("issues");

  private final String tableName;

  NotificationRefType(String tableName) {
    this.tableName = tableName;
  }

  /** The table whose id {@code source_ref_id} holds. */
  public String tableName() {
    return tableName;
  }
}
