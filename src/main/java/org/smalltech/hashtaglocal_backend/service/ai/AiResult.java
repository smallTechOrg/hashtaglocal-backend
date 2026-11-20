package org.smalltech.hashtaglocal_backend.service.ai;

import java.time.Duration;

/**
 * Outcome of one AI call: the reply text, or why it failed.
 *
 * @param retryAfter how long the provider asked us to wait, if it said; null otherwise
 * @param wholeKey true when the failure is about the key itself (bad key, no credit), so every
 *     model on that key should be skipped, not just this one
 * @param detail short, key-free description for logs
 */
public record AiResult(
    String text, AiFailure failure, Duration retryAfter, boolean wholeKey, String detail) {

  public static AiResult success(String text) {
    return new AiResult(text, null, null, false, null);
  }

  public static AiResult failure(AiFailure failure, String detail) {
    return new AiResult(null, failure, null, false, detail);
  }

  public static AiResult failure(AiFailure failure, Duration retryAfter, String detail) {
    return new AiResult(null, failure, retryAfter, false, detail);
  }

  public static AiResult keyFailure(AiFailure failure, String detail) {
    return new AiResult(null, failure, null, true, detail);
  }

  public boolean isSuccess() {
    return failure == null;
  }
}
