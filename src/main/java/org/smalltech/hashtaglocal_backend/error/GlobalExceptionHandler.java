package org.smalltech.hashtaglocal_backend.error;

import java.time.Instant;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {

	@ExceptionHandler(org.springframework.web.bind.MissingServletRequestParameterException.class)
	public ResponseEntity<ApiErrorResponse> handleMissingParam(
			org.springframework.web.bind.MissingServletRequestParameterException ex) {
		var errorItem = ApiErrorResponse.ErrorItem.builder().type(ErrorType.VALIDATION.name())
				.code(ErrorCode.VALIDATION_FAILED.name())
				.message("Missing required parameter: " + ex.getParameterName()).build();

		var body = ApiErrorResponse.ErrorBody.builder().message("Invalid request parameters").errors(List.of(errorItem))
				.timestamp(Instant.now()).build();

		return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ApiErrorResponse.builder().error(body).build());
	}

	@ExceptionHandler(ApiException.class)
	public ResponseEntity<ApiErrorResponse> handleApiException(ApiException ex) {

		var errorItem = ApiErrorResponse.ErrorItem.builder().type(ex.getType().name()).code(ex.getCode().name())
				.message(ex.getMessage()).build();

		var body = ApiErrorResponse.ErrorBody.builder().message(ex.getMessage()).errors(List.of(errorItem))
				.timestamp(Instant.now()).build();

		return ResponseEntity.status(ex.getStatus()).body(ApiErrorResponse.builder().error(body).build());
	}

	@ExceptionHandler(Exception.class)
	public ResponseEntity<ApiErrorResponse> handleUnhandledException(Exception ex) {

		var errorItem = ApiErrorResponse.ErrorItem.builder().type(ErrorType.SYSTEM.name())
				.code(ErrorCode.INTERNAL_ERROR.name()).message("Something went wrong").build();

		var body = ApiErrorResponse.ErrorBody.builder().message("Unexpected error").errors(List.of(errorItem))
				.timestamp(Instant.now()).build();

		return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
				.body(ApiErrorResponse.builder().error(body).build());
	}
}
