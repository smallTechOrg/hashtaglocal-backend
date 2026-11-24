package org.smalltech.hashtaglocal_backend.model.response;

import lombok.Builder;
import lombok.Value;

/** Result of "Test connection". {@code message} is sanitized and never holds a key. */
@Value
@Builder
public class AiConnectionTestData {
  boolean success;
  String provider;
  String endpoint;
  String model;
  long latencyMs;
  String message;
}
