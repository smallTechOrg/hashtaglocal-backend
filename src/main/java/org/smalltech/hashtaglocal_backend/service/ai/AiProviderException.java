package org.smalltech.hashtaglocal_backend.service.ai;

import org.smalltech.hashtaglocal_backend.exception.DownstreamServiceException;
import org.springframework.http.HttpStatus;

/**
 * The admin-configured AI provider could not be used (bad key, unknown model, blocked endpoint,
 * rate limit, outage, undecryptable key). Returned to the caller as 502 AI_PROVIDER_ERROR. The
 * message is already sanitized: it never holds a key.
 */
public class AiProviderException extends DownstreamServiceException {

  public AiProviderException(String message) {
    super(HttpStatus.BAD_GATEWAY, "AI_PROVIDER_ERROR", message);
  }
}
