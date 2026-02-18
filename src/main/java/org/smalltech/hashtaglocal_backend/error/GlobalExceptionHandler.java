package org.smalltech.hashtaglocal_backend.error;

import jakarta.servlet.http.HttpServletRequest;
import java.time.Instant;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {

	@ExceptionHandler(ApiException.class)
	public ResponseEntity<ApiErrorResponse> handleApiException(ApiException ex, HttpServletRequest request) {

		ApiErrorResponse.ErrorItem item = ApiErrorResponse.ErrorItem.builder().type(ex.getType().name())
				.code(ex.getCode().name()).message(ex.getMessage()).field(ex.getField()).build();

		ApiErrorResponse.ErrorBody body = ApiErrorResponse.ErrorBody.builder().message(ex.getMessage())
				.errors(List.of(item)).requestId(request.getHeader("X-Request-Id")).timestamp(Instant.now()).build();

		return ResponseEntity.status(ex.getStatus()).body(ApiErrorResponse.builder().error(body).build());
	}

	@ExceptionHandler(Exception.class)
	public ResponseEntity<ApiErrorResponse> handleGeneric(Exception ex) {

		ApiErrorResponse.ErrorItem item = ApiErrorResponse.ErrorItem.builder().type(ErrorType.SYSTEM.name())
				.code(ErrorCode.INTERNAL_ERROR.name()).message("Something went wrong").build();

		ApiErrorResponse.ErrorBody body = ApiErrorResponse.ErrorBody.builder().message("Unexpected error")
				.errors(List.of(item)).timestamp(Instant.now()).build();

		return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
				.body(ApiErrorResponse.builder().error(body).build());
	}
}
