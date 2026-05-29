package org.smalltech.hashtaglocal_backend.util;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.Base64;

/**
 * Opaque keyset cursor for the feed timeline, encoding the {@code (createdAt, id)} of the last row
 * of a page. Base64 of {@code "<epochMillisLikeIso>|<id>"}. See FEED_DESIGN.md §5 (read).
 */
public record FeedCursor(LocalDateTime createdAt, Long id) {

  public String encode() {
    String raw = createdAt.toString() + "|" + id;
    return Base64.getUrlEncoder()
        .withoutPadding()
        .encodeToString(raw.getBytes(StandardCharsets.UTF_8));
  }

  /**
   * Decodes a cursor; returns {@code null} for a null/blank/invalid cursor (treated as first page).
   */
  public static FeedCursor decode(String cursor) {
    if (cursor == null || cursor.isBlank()) {
      return null;
    }
    try {
      String raw = new String(Base64.getUrlDecoder().decode(cursor), StandardCharsets.UTF_8);
      int sep = raw.lastIndexOf('|');
      if (sep < 0) {
        return null;
      }
      LocalDateTime createdAt = LocalDateTime.parse(raw.substring(0, sep));
      Long id = Long.valueOf(raw.substring(sep + 1));
      return new FeedCursor(createdAt, id);
    } catch (RuntimeException e) {
      return null;
    }
  }
}
