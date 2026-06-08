package org.smalltech.hashtaglocal_backend.service.impl;

import com.google.api.client.googleapis.auth.oauth2.GoogleIdToken;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdTokenVerifier;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.jackson2.JacksonFactory;
import jakarta.transaction.Transactional;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.smalltech.hashtaglocal_backend.entity.UserAuthProviderEntity;
import org.smalltech.hashtaglocal_backend.entity.UserAuthSessionEntity;
import org.smalltech.hashtaglocal_backend.entity.UserEntity;
import org.smalltech.hashtaglocal_backend.model.GoogleUserResponse;
import org.smalltech.hashtaglocal_backend.model.Platform;
import org.smalltech.hashtaglocal_backend.model.TokenResponse;
import org.smalltech.hashtaglocal_backend.model.request.OAuthRequest;
import org.smalltech.hashtaglocal_backend.model.response.AuthTokenResponseData;
import org.smalltech.hashtaglocal_backend.repository.UserAuthProviderRepository;
import org.smalltech.hashtaglocal_backend.repository.UserAuthSessionRepository;
import org.smalltech.hashtaglocal_backend.repository.UserRepository;
import org.smalltech.hashtaglocal_backend.service.OAuthService;
import org.smalltech.hashtaglocal_backend.service.TokenService;
import org.smalltech.hashtaglocal_backend.util.UsernameUtil;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;

@Service
@Transactional
public class GoogleAuthService implements OAuthService {

  private static final String PROVIDER_TYPE = "google";
  private static final String TOKEN_URL = "https://oauth2.googleapis.com/token";
  private static final String USERINFO_URL = "https://www.googleapis.com/oauth2/v2/userinfo";

  @Value("${google.oauth.client-id}")
  private String clientId;

  @Value("${google.oauth.client-secret}")
  private String clientSecret;

  @Value("${google.oauth.redirect-uri}")
  private String redirectUri;

  private final UserRepository userRepository;
  private final UserAuthProviderRepository userAuthProviderRepository;
  private final UserAuthSessionRepository userAuthSessionRepository;
  private final TokenService tokenService;
  private final UsernameUtil usernameUtil;

  public GoogleAuthService(
      UserRepository userRepository,
      UserAuthProviderRepository userAuthProviderRepository,
      UserAuthSessionRepository userAuthSessionRepository,
      TokenService tokenService,
      UsernameUtil usernameUtil) {

    this.userRepository = userRepository;
    this.userAuthProviderRepository = userAuthProviderRepository;
    this.userAuthSessionRepository = userAuthSessionRepository;
    this.tokenService = tokenService;
    this.usernameUtil = usernameUtil;
  }

  @Override
  public String getProviderType() {
    return PROVIDER_TYPE;
  }

  @Override
  public AuthTokenResponseData authenticate(OAuthRequest request) {
    if (request.getAccessToken() != null && !request.getAccessToken().isBlank()) {
      return handleAccessToken(
          request.getAccessToken(),
          request.getNotificationToken(),
          request.getPlatform(),
          request.getDeviceId());
    }
    return handleAuthorizationCode(
        request.getCode(),
        request.getCodeVerifier(),
        request.getRedirectUri(),
        request.getNotificationToken(),
        request.getPlatform(),
        request.getDeviceId());
  }

  public AuthTokenResponseData handleAuthorizationCode(
      String code,
      String codeVerifier,
      String clientRedirectUri,
      String notificationToken,
      Platform platform,
      String deviceId) {

    System.out.println("Exchanging auth code for Google tokens");

    String effectiveRedirectUri =
        (clientRedirectUri != null && !clientRedirectUri.isEmpty())
            ? clientRedirectUri
            : redirectUri;

    RestTemplate restTemplate = new RestTemplate();

    MultiValueMap<String, String> body = new LinkedMultiValueMap<>();
    body.add("code", code);
    body.add("client_id", clientId);
    body.add("client_secret", clientSecret);
    body.add("redirect_uri", effectiveRedirectUri);
    body.add("grant_type", "authorization_code");

    if (codeVerifier != null && !codeVerifier.isEmpty()) {
      body.add("code_verifier", codeVerifier);
    }

    @SuppressWarnings("unchecked")
    Map<String, Object> tokenResponse = restTemplate.postForObject(TOKEN_URL, body, Map.class);

    String idTokenString = tokenResponse.get("id_token").toString();

    System.out.println("Google ID Token received");

    GoogleIdToken.Payload payload = verifyIdToken(idTokenString);

    return loginOrSignup(
        payload.getSubject(),
        payload.getEmail(),
        (String) payload.get("picture"),
        (String) payload.get("name"),
        notificationToken,
        platform,
        deviceId);
  }

  public AuthTokenResponseData handleAccessToken(
      String accessToken, String notificationToken, Platform platform, String deviceId) {

    System.out.println("Fetching Google user info");

    RestTemplate restTemplate = new RestTemplate();

    GoogleUserResponse googleUser =
        restTemplate.getForObject(
            USERINFO_URL + "?access_token=" + accessToken, GoogleUserResponse.class);

    return loginOrSignup(
        googleUser.getId(),
        googleUser.getEmail(),
        googleUser.getPicture(),
        googleUser.getName(),
        notificationToken,
        platform,
        deviceId);
  }

  private AuthTokenResponseData loginOrSignup(
      String providerUserId,
      String email,
      String picture,
      String name,
      String notificationToken,
      Platform platform,
      String deviceId) {

    System.out.println("Google userId: " + providerUserId);

    Optional<UserAuthProviderEntity> existingProvider =
        userAuthProviderRepository.findByProviderTypeAndProviderUserId(
            PROVIDER_TYPE, providerUserId);

    UserEntity user;
    UserAuthProviderEntity provider;
    boolean isNewUser;

    if (existingProvider.isPresent()) {
      System.out.println("Existing user found");

      provider = existingProvider.get();
      user = provider.getUser();
      isNewUser = false;
    } else {
      System.out.println("Creating new user");

      String baseUsername =
          usernameUtil.normalizeUsername(
              name != null ? name : (email != null ? email.split("@")[0] : "google"));

      String uniqueUsername = usernameUtil.generateUniqueUsername(baseUsername);

      user =
          userRepository.save(
              UserEntity.builder()
                  .username(uniqueUsername)
                  .locale("en")
                  .profilePicture(picture)
                  .build());

      System.out.println("User saved | ID: " + user.getId());

      provider =
          userAuthProviderRepository.save(
              UserAuthProviderEntity.builder()
                  .user(user)
                  .providerType(PROVIDER_TYPE)
                  .providerUserId(providerUserId)
                  .email(email)
                  .build());

      System.out.println("Provider saved | ID: " + provider.getId());
      isNewUser = true;
    }

    return createSession(user, provider, isNewUser, notificationToken, platform, deviceId);
  }

  private static final int MAX_ACTIVE_SESSIONS = 10;

  private AuthTokenResponseData createSession(
      UserEntity user,
      UserAuthProviderEntity provider,
      boolean isNewUser,
      String notificationToken,
      Platform platform,
      String deviceId) {

    // Enforce per-user session cap — deactivate oldest sessions if over the limit
    List<Long> activeIds =
        userAuthSessionRepository.findActiveSessionIdsByUserIdOrderByCreatedAsc(user.getId());
    if (activeIds.size() >= MAX_ACTIVE_SESSIONS) {
      List<Long> toDeactivate = activeIds.subList(0, activeIds.size() - MAX_ACTIVE_SESSIONS + 1);
      userAuthSessionRepository.deactivateByIds(toDeactivate);
    }

    String accessToken = tokenService.generateToken();
    String refreshToken = tokenService.generateToken();

    UserAuthSessionEntity session =
        userAuthSessionRepository.save(
            UserAuthSessionEntity.builder()
                .user(user)
                .userAuthProvider(provider)
                .accessToken(accessToken)
                .refreshToken(refreshToken)
                .accessTokenExpiryTs(tokenService.accessExpiryEpochSeconds())
                .refreshTokenExpiryTs(tokenService.refreshExpiryEpochSeconds())
                .notificationToken(notificationToken)
                .platform(platform)
                .deviceId(deviceId)
                .isActive(true)
                .build());

    System.out.println("Session created | ID: " + session.getId());

    return AuthTokenResponseData.builder()
        .accessToken(
            TokenResponse.builder()
                .value(accessToken)
                .expiry(session.getAccessTokenExpiryTs())
                .build())
        .refreshToken(
            TokenResponse.builder()
                .value(refreshToken)
                .expiry(session.getRefreshTokenExpiryTs())
                .build())
        .isNewUser(isNewUser)
        .build();
  }

  private GoogleIdToken.Payload verifyIdToken(String idTokenString) {

    try {
      GoogleIdTokenVerifier verifier =
          new GoogleIdTokenVerifier.Builder(
                  new NetHttpTransport(), JacksonFactory.getDefaultInstance())
              .setAudience(Collections.singletonList(clientId))
              .build();

      GoogleIdToken idToken = verifier.verify(idTokenString);

      if (idToken == null) {
        throw new RuntimeException("Invalid ID token");
      }

      System.out.println("ID token verified");

      return idToken.getPayload();

    } catch (Exception e) {
      throw new RuntimeException("ID token verification failed", e);
    }
  }
}
