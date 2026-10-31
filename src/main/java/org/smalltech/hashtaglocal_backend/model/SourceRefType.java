package org.smalltech.hashtaglocal_backend.model;

/**
 * Which table a polymorphic {@code source_ref_id} points at (no FK by design — the ids live in
 * different tables). Not tied to any one feature: any module with a {@code source_ref_type} /
 * {@code source_ref_id} pair can use it. Each constant is the entity for one table, so {@code
 * source_ref_type = BULLETIN} with {@code source_ref_id = 7} means {@code bulletins.id = 7}.
 *
 * <p>To reference another table, add a constant here and widen the column's CHECK constraint.
 */
public enum SourceRefType {
  /** {@code bulletins.id} */
  BULLETIN("bulletins"),
  /** {@code issues.id} */
  ISSUE("issues");

  private final String tableName;

  SourceRefType(String tableName) {
    this.tableName = tableName;
  }

  /** The table whose id {@code source_ref_id} holds. */
  public String tableName() {
    return tableName;
  }
}
