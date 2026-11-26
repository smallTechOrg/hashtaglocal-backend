package org.smalltech.hashtaglocal_backend.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

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

  @Test
  void compareOrdersByNumberNotText() {
    assertTrue(AppVersionUtil.compare("1.0.10", "1.0.9") > 0);
    assertTrue(AppVersionUtil.compare("2.0.0", "1.99.99") > 0);
    assertTrue(AppVersionUtil.compare("1.2", "1.2.1") < 0);
    assertEquals(0, AppVersionUtil.compare("1.2.3", "1.2.3"));
  }

  @Test
  void compareHandlesSuffixesAndJunkWithoutThrowing() {
    assertTrue(AppVersionUtil.compare("1.3-beta", "1.2.9") > 0);
    assertTrue(AppVersionUtil.compare("dev", "0.0.1") < 0);
    assertTrue(AppVersionUtil.compare("1.0." + "9".repeat(40), "1.0.1") > 0);
  }
}
