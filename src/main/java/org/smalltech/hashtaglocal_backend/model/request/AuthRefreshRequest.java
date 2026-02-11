package org.smalltech.hashtaglocal_backend.model.request;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AuthRefreshRequest {
	@JsonProperty("refresh_token")
	private String refreshToken;
}
