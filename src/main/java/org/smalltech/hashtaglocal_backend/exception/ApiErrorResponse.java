package org.smalltech.hashtaglocal_backend.exception;

import java.util.List;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class ApiErrorResponse {

  private ErrorEnvelope error;

  @Data
  @Builder
  public static class ErrorEnvelope {
    private String message;
    private List<ApiError> errors;
  }

  @Data
  @Builder
  public static class ApiError {
    private String type; // VALIDATION, AUTH, PERMISSION, GEO, etc.
    private String message; // human readable
  }
}
