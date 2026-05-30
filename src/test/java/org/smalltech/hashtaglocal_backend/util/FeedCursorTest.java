package org.smalltech.hashtaglocal_backend.util;

import static org.junit.jupiter.api.Assertions.*;

import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;

class FeedCursorTest {

  @Test
  void roundTripsCreatedAtAndId() {
    LocalDateTime ts = LocalDateTime.of(2026, 5, 29, 12, 34, 56, 789_000_000);
    FeedCursor original = new FeedCursor(ts, 42L);

    FeedCursor decoded = FeedCursor.decode(original.encode());

    assertNotNull(decoded);
    assertEquals(ts, decoded.createdAt());
    assertEquals(42L, decoded.id());
  }

  @Test
  void decodeNullOrBlankReturnsNull() {
    assertNull(FeedCursor.decode(null));
    assertNull(FeedCursor.decode(""));
    assertNull(FeedCursor.decode("   "));
  }

  @Test
  void decodeGarbageReturnsNullInsteadOfThrowing() {
    assertNull(FeedCursor.decode("not-a-valid-cursor!!!"));
  }
}
