package org.smalltech.hashtaglocal_backend.error;

import java.time.Instant;
import java.util.List;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class ApiErrorResponse {

	private ErrorBody error;

	@Data
	@Builder
	public static class ErrorBody {
		private String message;
		private List<ErrorItem> errors;
		private Instant timestamp;
	}

	@Data
	@Builder
	public static class ErrorItem {
		private String type;
		private String code;
		private String message;
	}
}
