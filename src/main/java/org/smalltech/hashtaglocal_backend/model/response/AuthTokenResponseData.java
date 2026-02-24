package org.smalltech.hashtaglocal_backend.model.response;

import lombok.Builder;
import lombok.Data;
import org.smalltech.hashtaglocal_backend.model.TokenResponse;

@Data
@Builder
public class AuthTokenResponseData {
	private TokenResponse accessToken;
	private TokenResponse refreshToken;
}
