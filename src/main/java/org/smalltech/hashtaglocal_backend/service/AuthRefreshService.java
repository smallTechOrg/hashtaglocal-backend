package org.smalltech.hashtaglocal_backend.service;

import jakarta.transaction.Transactional;
import java.util.Optional;
import org.smalltech.hashtaglocal_backend.entity.UserAuthSessionEntity;
import org.smalltech.hashtaglocal_backend.model.APIResponse;
import org.smalltech.hashtaglocal_backend.model.ResponseData;
import org.smalltech.hashtaglocal_backend.model.TokenResponse;
import org.smalltech.hashtaglocal_backend.repository.UserAuthSessionRepository;
import org.springframework.stereotype.Service;

@Service
@Transactional
public class AuthRefreshService {

	private final UserAuthSessionRepository userAuthSessionRepository;
	private final TokenService tokenService;

	public AuthRefreshService(UserAuthSessionRepository userAuthSessionRepository, TokenService tokenService) {
		this.userAuthSessionRepository = userAuthSessionRepository;
		this.tokenService = tokenService;
	}

	public APIResponse refreshTokens(String refreshToken) {

		System.out.println("Attempting to refresh tokens");

		// Find the session by refresh token
		Optional<UserAuthSessionEntity> sessionOpt = userAuthSessionRepository.findByRefreshToken(refreshToken);

		if (sessionOpt.isEmpty()) {
			System.out.println(" Refresh token not found");
			throw new RuntimeException("Invalid refresh token");
		}

		UserAuthSessionEntity session = sessionOpt.get();

		// Check if session is active
		if (!session.getIsActive()) {
			System.out.println(" Session is inactive");
			throw new RuntimeException("Session is inactive");
		}

		long currentEpochSeconds = tokenService.nowEpochSeconds();

		// Check if refresh token is expired
		if (session.getRefreshTokenExpiryTs() < currentEpochSeconds) {
			System.out.println(" Refresh token expired");
			throw new RuntimeException("Refresh token has expired");
		}

		System.out.println(" Valid refresh token found | Session ID: " + session.getId());

		// Check access token expiry
		if (session.getAccessTokenExpiryTs() > currentEpochSeconds) {
			System.out.println(" Access token is still active, returning existing tokens");
			return APIResponse.builder()
					.data(ResponseData.builder()
							.accessToken(TokenResponse.builder().value(session.getAccessToken())
									.expiry(session.getAccessTokenExpiryTs()).build())
							.refreshToken(TokenResponse.builder().value(session.getRefreshToken())
									.expiry(session.getRefreshTokenExpiryTs()).build())
							.build())
					.build();
		}

		// Generate new tokens
		String newAccessToken = tokenService.generateToken();
		String newRefreshToken = tokenService.generateToken();

		long newAccessTokenExpiry = tokenService.accessExpiryEpochSeconds();
		long newRefreshTokenExpiry = tokenService.refreshExpiryEpochSeconds();

		// Update session with new tokens
		session.setAccessToken(newAccessToken);
		session.setAccessTokenExpiryTs(newAccessTokenExpiry);
		session.setRefreshToken(newRefreshToken);
		session.setRefreshTokenExpiryTs(newRefreshTokenExpiry);

		userAuthSessionRepository.save(session);

		System.out.println("Tokens refreshed | Session ID: " + session.getId());

		return APIResponse.builder()
				.data(ResponseData.builder()
						.accessToken(TokenResponse.builder().value(newAccessToken).expiry(newAccessTokenExpiry).build())
						.refreshToken(
								TokenResponse.builder().value(newRefreshToken).expiry(newRefreshTokenExpiry).build())
						.build())
				.build();
	}
}
