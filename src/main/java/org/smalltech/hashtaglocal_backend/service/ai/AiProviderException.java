package org.smalltech.hashtaglocal_backend.service.ai;

import org.smalltech.hashtaglocal_backend.exception.DownstreamServiceException;
import org.springframework.http.HttpStatus;

/**
 * The admin-configured AI provider could not be used (bad key, unknown model, blocked endpoint,
 * rate limit, outage, undecryptable key). Returned to the caller as 502 AI_PROVIDER_ERROR. The
 * message is already sanitized: it never holds a key.
 */
public class AiProviderException extends DownstreamServiceException {

  /** Why the provider call failed; null when no call was made (e.g. the key is unreadable). */
  private final AiFailure failure;

  public AiProviderException(String message) {
    this(message, null);
  }

  public AiProviderException(String message, AiFailure failure) {
    super(HttpStatus.BAD_GATEWAY, "AI_PROVIDER_ERROR", message);
    this.failure = failure;
  }

  public AiFailure getFailure() {
    return failure;
  }

  /**
   * True for a short-lived failure (rate limit, outage, timeout) that may pass on its own. False
   * for a config problem (bad key, unknown model, used-up quota, unreadable key) that needs an
   * admin to fix it, so callers should stop rather than keep calling.
   */
  public boolean isTemporary() {
    return failure == AiFailure.RATE_LIMITED || failure == AiFailure.TRANSIENT;
  }
}
