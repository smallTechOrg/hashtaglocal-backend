package org.smalltech.hashtaglocal_backend.service;

import jakarta.transaction.Transactional;
import java.util.Optional;
import org.smalltech.hashtaglocal_backend.entity.UserAuthSessionEntity;
import org.smalltech.hashtaglocal_backend.model.TokenResponse;
import org.smalltech.hashtaglocal_backend.model.response.AuthTokenResponseData;
import org.smalltech.hashtaglocal_backend.repository.UserAuthSessionRepository;
import org.springframework.stereotype.Service;

@Service
@Transactional
public class AuthRefreshService {

  private final UserAuthSessionRepository userAuthSessionRepository;
  private final TokenService tokenService;

  public AuthRefreshService(
      UserAuthSessionRepository userAuthSessionRepository, TokenService tokenService) {
    this.userAuthSessionRepository = userAuthSessionRepository;
    this.tokenService = tokenService;
  }

  public AuthTokenResponseData refreshTokens(
      String refreshToken, String notificationToken, String deviceId) {

    System.out.println("Attempting to refresh tokens");

    Optional<UserAuthSessionEntity> sessionOpt =
        userAuthSessionRepository.findByRefreshToken(refreshToken);

    if (sessionOpt.isEmpty()) {
      System.out.println(" Refresh token not found");
      throw new RuntimeException("Invalid refresh token");
    }

    UserAuthSessionEntity session = sessionOpt.get();

    if (!session.getIsActive()) {
      System.out.println(" Session is inactive");
      throw new RuntimeException("Session is inactive");
    }

    long currentEpochSeconds = tokenService.nowEpochSeconds();

    Long refreshExpiry = session.getRefreshTokenExpiryTs();
    if (refreshExpiry == null || refreshExpiry < currentEpochSeconds) {
      System.out.println(" Refresh token expired");
      throw new RuntimeException("Refresh token has expired");
    }

    System.out.println(" Valid refresh token found | Session ID: " + session.getId());

    // Always apply side-channel updates when provided, regardless of whether
    // auth tokens are rotated — keeps session metadata current.
    if (notificationToken != null) {
      session.setNotificationToken(notificationToken);
    }
    // Backfill device_id for sessions created before it was tracked (e.g. Google OAuth sessions).
    if (deviceId != null && session.getDeviceId() == null) {
      session.setDeviceId(deviceId);
    }

    Long accessExpiry = session.getAccessTokenExpiryTs();
    if (accessExpiry != null && accessExpiry > currentEpochSeconds) {
      System.out.println(" Access token is still active, returning existing tokens");
      // Save only if we updated the notification token
      if (notificationToken != null) {
        userAuthSessionRepository.save(session);
      }
      return AuthTokenResponseData.builder()
          .accessToken(
              TokenResponse.builder()
                  .value(session.getAccessToken())
                  .expiry(session.getAccessTokenExpiryTs())
                  .build())
          .refreshToken(
              TokenResponse.builder()
                  .value(session.getRefreshToken())
                  .expiry(session.getRefreshTokenExpiryTs())
                  .build())
          .build();
    }

    String newAccessToken = tokenService.generateToken();
    String newRefreshToken = tokenService.generateToken();

    long newAccessTokenExpiry = tokenService.accessExpiryEpochSeconds();
    long newRefreshTokenExpiry = tokenService.refreshExpiryEpochSeconds();

    session.setAccessToken(newAccessToken);
    session.setAccessTokenExpiryTs(newAccessTokenExpiry);
    session.setRefreshToken(newRefreshToken);
    session.setRefreshTokenExpiryTs(newRefreshTokenExpiry);

    userAuthSessionRepository.save(session);

    System.out.println("Tokens refreshed | Session ID: " + session.getId());

    return AuthTokenResponseData.builder()
        .accessToken(
            TokenResponse.builder().value(newAccessToken).expiry(newAccessTokenExpiry).build())
        .refreshToken(
            TokenResponse.builder().value(newRefreshToken).expiry(newRefreshTokenExpiry).build())
        .build();
  }
}
