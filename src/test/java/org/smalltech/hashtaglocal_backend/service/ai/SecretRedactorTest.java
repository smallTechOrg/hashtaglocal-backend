package org.smalltech.hashtaglocal_backend.service.ai;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import org.junit.jupiter.api.Test;

class SecretRedactorTest {

  @Test
  void masksKeyShapedTextAndTheKnownKey() {
    String key = "myCustomKey-987";
    String text =
        "Incorrect API key provided: sk-proj-ab***********yz. Also gsk_abcdef123 and AIzaSyA1234567890"
            + " and Bearer abc.def.ghi and "
            + key;

    String redacted = SecretRedactor.redact(text, key);

    assertFalse(redacted.contains("sk-proj"));
    assertFalse(redacted.contains("gsk_"));
    assertFalse(redacted.contains("AIza"));
    assertFalse(redacted.contains("abc.def"));
    assertFalse(redacted.contains(key));
  }

  @Test
  void leavesOrdinaryMessagesAlone() {
    assertEquals("401 invalid x-api-key", SecretRedactor.redact("401 invalid x-api-key"));
  }

  @Test
  void leavesWordsThatOnlyContainKeyPrefixesAlone() {
    String text = "risk-assessment failed in task-manager; disk-usage high; Risk-Based routing";
    assertEquals(text, SecretRedactor.redact(text));
  }

  @Test
  void stillMasksAKeyAfterPunctuation() {
    assertEquals(
        "key=[REDACTED] (\"[REDACTED]\")",
        SecretRedactor.redact("key=sk-abc123xyz (\"gsk_abcdef12\")"));
  }
}
