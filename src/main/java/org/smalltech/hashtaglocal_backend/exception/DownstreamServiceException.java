package org.smalltech.hashtaglocal_backend.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
public class DownstreamServiceException extends RuntimeException {

  private final HttpStatus status;
  private final String type;

  public DownstreamServiceException(HttpStatus status, String type, String message) {
    super(message);
    this.status = status;
    this.type = type;
  }
}
