package org.smalltech.hashtaglocal_backend.model;

public class AuthResponse {

	private String accessToken;
	private long accessTokenExpiryTs;
	private String refreshToken;
	private long refreshTokenExpiryTs;
	private Long userId;
	private Long providerId;
	private String email;

	public AuthResponse() {
	}

	public AuthResponse(String accessToken, long accessTokenExpiryTs, String refreshToken, long refreshTokenExpiryTs,
			Long userId, Long providerId) {
		this(accessToken, accessTokenExpiryTs, refreshToken, refreshTokenExpiryTs, userId, providerId, null);
	}

	public AuthResponse(String accessToken, long accessTokenExpiryTs, String refreshToken, long refreshTokenExpiryTs,
			Long userId, Long providerId, String email) {
		this.accessToken = accessToken;
		this.accessTokenExpiryTs = accessTokenExpiryTs;
		this.refreshToken = refreshToken;
		this.refreshTokenExpiryTs = refreshTokenExpiryTs;
		this.userId = userId;
		this.providerId = providerId;
		this.email = email;
	}

	public String getAccessToken() {
		return accessToken;
	}

	public long getAccessTokenExpiryTs() {
		return accessTokenExpiryTs;
	}

	public String getRefreshToken() {
		return refreshToken;
	}

	public long getRefreshTokenExpiryTs() {
		return refreshTokenExpiryTs;
	}

	public Long getUserId() {
		return userId;
	}

	public Long getProviderId() {
		return providerId;
	}

	public String getEmail() {
		return email;
	}
}
