package org.smalltech.hashtaglocal_backend.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.Test;

class AppVersionUtilTest {

  @Test
  void validVersionIsReturned() {
    assertEquals("1.2.3", AppVersionUtil.sanitize("1.2.3"));
  }

  @Test
  void versionIsTrimmed() {
    assertEquals("1.2.3", AppVersionUtil.sanitize("  1.2.3 "));
  }

  @Test
  void nullAndBlankBecomeNull() {
    assertNull(AppVersionUtil.sanitize(null));
    assertNull(AppVersionUtil.sanitize(""));
    assertNull(AppVersionUtil.sanitize("   "));
  }

  @Test
  void oversizedVersionBecomesNull() {
    assertEquals(
        "9".repeat(AppVersionUtil.MAX_LENGTH),
        AppVersionUtil.sanitize("9".repeat(AppVersionUtil.MAX_LENGTH)));
    assertNull(AppVersionUtil.sanitize("9".repeat(AppVersionUtil.MAX_LENGTH + 1)));
  }
}
