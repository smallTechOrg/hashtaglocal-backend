package org.smalltech.hashtaglocal_backend.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.smalltech.hashtaglocal_backend.entity.UserAuthSessionEntity;
import org.smalltech.hashtaglocal_backend.model.TokenResponse;
import org.smalltech.hashtaglocal_backend.model.response.AuthTokenResponseData;
import org.smalltech.hashtaglocal_backend.repository.UserAuthSessionRepository;

@ExtendWith(MockitoExtension.class)
class AuthRefreshServiceTest {

  @Mock private UserAuthSessionRepository userAuthSessionRepository;

  @Mock private TokenService tokenService;

  private AuthRefreshService authRefreshService;

  private static final String REFRESH_TOKEN = "test-refresh-token";
  private static final String ACCESS_TOKEN = "test-access-token";
  private static final long SESSION_ID = 1L;
  private static final long CURRENT_TIME = 1000L;
  private static final long FUTURE_TIME = 2000L;
  private static final long PAST_TIME = 500L;

  @BeforeEach
  void setUp() {
    authRefreshService = new AuthRefreshService(userAuthSessionRepository, tokenService);
  }

  private UserAuthSessionEntity createActiveSession(
      String accessToken, long accessTokenExpiry, String refreshToken, long refreshTokenExpiry) {
    UserAuthSessionEntity session = new UserAuthSessionEntity();
    session.setId(SESSION_ID);
    session.setAccessToken(accessToken);
    session.setAccessTokenExpiryTs(accessTokenExpiry);
    session.setRefreshToken(refreshToken);
    session.setRefreshTokenExpiryTs(refreshTokenExpiry);
    session.setIsActive(true);
    return session;
  }

  @Test
  void testRefreshTokens_WithValidRefreshToken_AndExpiredAccessToken_ShouldGenerateNewTokens() {
    // Arrange
    UserAuthSessionEntity session =
        createActiveSession(
            ACCESS_TOKEN,
            PAST_TIME, // access token expired
            REFRESH_TOKEN,
            FUTURE_TIME); // refresh token valid

    when(userAuthSessionRepository.findByRefreshToken(REFRESH_TOKEN))
        .thenReturn(Optional.of(session));
    when(tokenService.nowEpochSeconds()).thenReturn(CURRENT_TIME);

    String newAccessToken = "new-access-token";
    String newRefreshToken = "new-refresh-token";
    long newAccessTokenExpiry = 3000L;
    long newRefreshTokenExpiry = 4000L;

    when(tokenService.generateToken()).thenReturn(newAccessToken).thenReturn(newRefreshToken);
    when(tokenService.accessExpiryEpochSeconds()).thenReturn(newAccessTokenExpiry);
    when(tokenService.refreshExpiryEpochSeconds()).thenReturn(newRefreshTokenExpiry);

    // Act
    AuthTokenResponseData response = authRefreshService.refreshTokens(REFRESH_TOKEN, null, null);

    // Assert
    assertNotNull(response);
    assertNotNull(response.getAccessToken());
    assertNotNull(response.getRefreshToken());

    TokenResponse accessTokenResponse = response.getAccessToken();
    TokenResponse refreshTokenResponse = response.getRefreshToken();

    assertEquals(newAccessToken, accessTokenResponse.getValue());
    assertEquals(newAccessTokenExpiry, accessTokenResponse.getExpiry());
    assertEquals(newRefreshToken, refreshTokenResponse.getValue());
    assertEquals(newRefreshTokenExpiry, refreshTokenResponse.getExpiry());

    verify(userAuthSessionRepository).findByRefreshToken(REFRESH_TOKEN);
    verify(userAuthSessionRepository).save(any(UserAuthSessionEntity.class));
    verify(tokenService, times(2)).generateToken();
    verify(tokenService).accessExpiryEpochSeconds();
    verify(tokenService).refreshExpiryEpochSeconds();
  }

  @Test
  void testRefreshTokens_WithValidRefreshToken_AndActiveAccessToken_ShouldReturnExistingTokens() {
    // Arrange
    UserAuthSessionEntity session =
        createActiveSession(
            ACCESS_TOKEN,
            FUTURE_TIME, // access token still active
            REFRESH_TOKEN,
            FUTURE_TIME); // refresh token valid

    when(userAuthSessionRepository.findByRefreshToken(REFRESH_TOKEN))
        .thenReturn(Optional.of(session));
    when(tokenService.nowEpochSeconds()).thenReturn(CURRENT_TIME);

    // Act
    AuthTokenResponseData response = authRefreshService.refreshTokens(REFRESH_TOKEN, null, null);

    // Assert
    assertNotNull(response);
    assertNotNull(response.getAccessToken());
    assertNotNull(response.getRefreshToken());

    TokenResponse accessTokenResponse = response.getAccessToken();
    TokenResponse refreshTokenResponse = response.getRefreshToken();

    // Should return existing tokens, not new ones
    assertEquals(ACCESS_TOKEN, accessTokenResponse.getValue());
    assertEquals(FUTURE_TIME, accessTokenResponse.getExpiry());
    assertEquals(REFRESH_TOKEN, refreshTokenResponse.getValue());
    assertEquals(FUTURE_TIME, refreshTokenResponse.getExpiry());

    // Database should NOT be called to save since tokens are still valid
    verify(userAuthSessionRepository).findByRefreshToken(REFRESH_TOKEN);
    verify(userAuthSessionRepository, never()).save(any(UserAuthSessionEntity.class));
    verify(tokenService, never()).generateToken();
  }

  @Test
  void testRefreshTokens_WithInvalidRefreshToken_ShouldThrowException() {
    // Arrange
    when(userAuthSessionRepository.findByRefreshToken(REFRESH_TOKEN)).thenReturn(Optional.empty());

    // Act & Assert
    RuntimeException exception =
        assertThrows(
            RuntimeException.class,
            () -> authRefreshService.refreshTokens(REFRESH_TOKEN, null, null));
    assertEquals("Invalid refresh token", exception.getMessage());

    verify(userAuthSessionRepository).findByRefreshToken(REFRESH_TOKEN);
    verify(tokenService, never()).generateToken();
  }

  @Test
  void testRefreshTokens_WithExpiredRefreshToken_ShouldThrowException() {
    // Arrange
    UserAuthSessionEntity session =
        createActiveSession(
            ACCESS_TOKEN,
            PAST_TIME, // access token expired
            REFRESH_TOKEN,
            PAST_TIME); // refresh token also expired

    when(userAuthSessionRepository.findByRefreshToken(REFRESH_TOKEN))
        .thenReturn(Optional.of(session));
    when(tokenService.nowEpochSeconds()).thenReturn(CURRENT_TIME);

    // Act & Assert
    RuntimeException exception =
        assertThrows(
            RuntimeException.class,
            () -> authRefreshService.refreshTokens(REFRESH_TOKEN, null, null));
    assertEquals("Refresh token has expired", exception.getMessage());

    verify(userAuthSessionRepository).findByRefreshToken(REFRESH_TOKEN);
    verify(userAuthSessionRepository, never()).save(any(UserAuthSessionEntity.class));
  }

  @Test
  void testRefreshTokens_WithInactiveSession_ShouldThrowException() {
    // Arrange
    UserAuthSessionEntity session =
        createActiveSession(ACCESS_TOKEN, FUTURE_TIME, REFRESH_TOKEN, FUTURE_TIME);
    session.setIsActive(false); // session is inactive

    when(userAuthSessionRepository.findByRefreshToken(REFRESH_TOKEN))
        .thenReturn(Optional.of(session));

    // Act & Assert
    RuntimeException exception =
        assertThrows(
            RuntimeException.class,
            () -> authRefreshService.refreshTokens(REFRESH_TOKEN, null, null));
    assertEquals("Session is inactive", exception.getMessage());

    verify(userAuthSessionRepository).findByRefreshToken(REFRESH_TOKEN);
    verify(userAuthSessionRepository, never()).save(any(UserAuthSessionEntity.class));
  }

  @Test
  void testRefreshTokens_SessionValidationOrder_InactiveSessionBeforeRefreshTokenExpiry() {
    // Arrange - session is inactive but refresh token is also expired
    // This tests that inactive session is checked before refresh token expiry
    UserAuthSessionEntity session =
        createActiveSession(ACCESS_TOKEN, FUTURE_TIME, REFRESH_TOKEN, PAST_TIME); // refresh
    // token
    // expired
    session.setIsActive(false);

    when(userAuthSessionRepository.findByRefreshToken(REFRESH_TOKEN))
        .thenReturn(Optional.of(session));

    // Act & Assert
    RuntimeException exception =
        assertThrows(
            RuntimeException.class,
            () -> authRefreshService.refreshTokens(REFRESH_TOKEN, null, null));
    // Should fail on inactive session check before refresh token expiry check
    assertEquals("Session is inactive", exception.getMessage());

    verify(userAuthSessionRepository).findByRefreshToken(REFRESH_TOKEN);
  }

  @Test
  void testRefreshTokens_NewTokensNotCreatedWhenAccessTokenStillValid() {
    // Arrange
    UserAuthSessionEntity session =
        createActiveSession(
            ACCESS_TOKEN,
            FUTURE_TIME, // access token still active
            REFRESH_TOKEN,
            FUTURE_TIME);

    when(userAuthSessionRepository.findByRefreshToken(REFRESH_TOKEN))
        .thenReturn(Optional.of(session));
    when(tokenService.nowEpochSeconds()).thenReturn(CURRENT_TIME);

    // Act
    authRefreshService.refreshTokens(REFRESH_TOKEN, null, null);

    // Assert
    // Verify that token generation methods were never called
    verify(tokenService, never()).generateToken();
    verify(tokenService, never()).accessExpiryEpochSeconds();
    verify(tokenService, never()).refreshExpiryEpochSeconds();
    // Verify that session was never saved (no new tokens to save)
    verify(userAuthSessionRepository, never()).save(any());
  }

  @Test
  void testRefreshTokens_UpdatesSessionWhenGeneratingNewTokens() {
    // Arrange
    UserAuthSessionEntity session =
        createActiveSession(
            ACCESS_TOKEN,
            PAST_TIME, // access token expired
            REFRESH_TOKEN,
            FUTURE_TIME);

    when(userAuthSessionRepository.findByRefreshToken(REFRESH_TOKEN))
        .thenReturn(Optional.of(session));
    when(tokenService.nowEpochSeconds()).thenReturn(CURRENT_TIME);

    String newAccessToken = "new-access-token";
    String newRefreshToken = "new-refresh-token";
    long newAccessTokenExpiry = 3000L;
    long newRefreshTokenExpiry = 4000L;

    when(tokenService.generateToken()).thenReturn(newAccessToken).thenReturn(newRefreshToken);
    when(tokenService.accessExpiryEpochSeconds()).thenReturn(newAccessTokenExpiry);
    when(tokenService.refreshExpiryEpochSeconds()).thenReturn(newRefreshTokenExpiry);

    // Act
    authRefreshService.refreshTokens(REFRESH_TOKEN, null, null);

    // Assert
    verify(userAuthSessionRepository)
        .save(
            argThat(
                savedSession ->
                    savedSession.getAccessToken().equals(newAccessToken)
                        && savedSession.getAccessTokenExpiryTs() == newAccessTokenExpiry
                        && savedSession.getRefreshToken().equals(newRefreshToken)
                        && savedSession.getRefreshTokenExpiryTs() == newRefreshTokenExpiry));
  }
}
