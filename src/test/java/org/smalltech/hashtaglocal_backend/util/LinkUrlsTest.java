package org.smalltech.hashtaglocal_backend.util;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class LinkUrlsTest {

  @Test
  void lowercasesSchemeAndHostAndDropsTrailingSlash() {
    assertEquals("https://example.com/path", LinkUrls.canonicalize("HTTPS://Example.COM/path/"));
  }

  @Test
  void stripsTrackingParams() {
    assertEquals(
        "https://example.com/a?id=5",
        LinkUrls.canonicalize("https://example.com/a?utm_source=x&id=5&fbclid=abc"));
  }

  @Test
  void keepsNonTrackingQuery() {
    assertEquals(
        "https://example.com?q=hello", LinkUrls.canonicalize("https://example.com?q=hello"));
  }

  @Test
  void nullInNullOut() {
    assertNull(LinkUrls.canonicalize(null));
  }
}
