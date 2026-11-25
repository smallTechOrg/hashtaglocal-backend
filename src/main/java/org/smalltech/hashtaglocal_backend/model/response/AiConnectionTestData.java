package org.smalltech.hashtaglocal_backend.model.response;

import lombok.Builder;
import lombok.Value;

/** Result of "Test". {@code message} is sanitized and never holds a key. */
@Value
@Builder
public class AiConnectionTestData {
  boolean success;
  String name;
  String endpoint;
  String model;
  long latencyMs;
  String message;
}
