package org.smalltech.hashtaglocal_backend.error;

import lombok.Builder;
import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
public class ApiException extends RuntimeException {

	private final HttpStatus status;
	private final ErrorType type;
	private final ErrorCode code;

	@Builder
	public ApiException(HttpStatus status, ErrorType type, ErrorCode code, String message) {
		super(message);
		this.status = status;
		this.type = type;
		this.code = code;
	}
}
