package org.smalltech.hashtaglocal_backend.model;

import lombok.*;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AuthResponse {

	private String accessToken;
	private long accessTokenExpiryTs;
	private String refreshToken;
	private long refreshTokenExpiryTs;
	private Long userId;
	private Long providerId;
	private String email;
}
