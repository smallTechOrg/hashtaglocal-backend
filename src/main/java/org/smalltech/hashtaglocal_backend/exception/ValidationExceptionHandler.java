package org.smalltech.hashtaglocal_backend.exception;

import java.util.List;
import java.util.stream.Collectors;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class ValidationExceptionHandler {

  @ExceptionHandler(MethodArgumentNotValidException.class)
  public ResponseEntity<ApiErrorResponse> handleValidation(MethodArgumentNotValidException ex) {

    List<ApiErrorResponse.ApiError> fieldErrors =
        ex.getBindingResult().getFieldErrors().stream()
            .map(
                err ->
                    ApiErrorResponse.ApiError.builder()
                        .type("VALIDATION")
                        .message(err.getField() + ": " + err.getDefaultMessage())
                        .build())
            .collect(Collectors.toList());

    ApiErrorResponse response =
        ApiErrorResponse.builder()
            .error(
                ApiErrorResponse.ErrorEnvelope.builder()
                    .message("Validation failed")
                    .errors(fieldErrors)
                    .build())
            .build();

    return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
  }
}
  @ExceptionHandler(DownstreamServiceException.class)
  public ResponseEntity<ApiErrorResponse> handleDownstreamFailure(DownstreamServiceException ex) {
    ApiErrorResponse response =
        ApiErrorResponse.builder()
            .error(
                ApiErrorResponse.ErrorEnvelope.builder()
                    .message(ex.getMessage())
                    .errors(
                        List.of(
                            ApiErrorResponse.ApiError.builder()
                                .type(ex.getType())
                                .message(ex.getMessage())
                                .build()))
                    .build())
            .build();

    return ResponseEntity.status(ex.getStatus()).body(response);
  }
