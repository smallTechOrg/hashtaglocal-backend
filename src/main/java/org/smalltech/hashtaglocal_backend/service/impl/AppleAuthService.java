package org.smalltech.hashtaglocal_backend.service.impl;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.jwk.source.JWKSource;
import com.nimbusds.jose.jwk.source.RemoteJWKSet;
import com.nimbusds.jose.proc.JWSVerificationKeySelector;
import com.nimbusds.jose.proc.SecurityContext;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.proc.ConfigurableJWTProcessor;
import com.nimbusds.jwt.proc.DefaultJWTProcessor;
import jakarta.transaction.Transactional;
import java.net.URL;
import java.util.Optional;
import org.smalltech.hashtaglocal_backend.entity.UserAuthProviderEntity;
import org.smalltech.hashtaglocal_backend.entity.UserAuthSessionEntity;
import org.smalltech.hashtaglocal_backend.entity.UserEntity;
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

@Service
@Transactional
public class AppleAuthService implements OAuthService {

  private static final String PROVIDER_TYPE = "apple";
  private static final String APPLE_JWKS_URL = "https://appleid.apple.com/auth/keys";
  private static final String APPLE_ISSUER = "https://appleid.apple.com";

  @Value("${apple.bundle-id}")
  private String bundleId;

  private final UserRepository userRepository;
  private final UserAuthProviderRepository userAuthProviderRepository;
  private final UserAuthSessionRepository userAuthSessionRepository;
  private final TokenService tokenService;
  private final UsernameUtil usernameUtil;

  public AppleAuthService(
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
    return handleIdentityToken(
        request.getIdentityToken(),
        request.getFullName(),
        request.getNotificationToken(),
        request.getPlatform());
  }

  public AuthTokenResponseData handleIdentityToken(
      String identityToken, String fullName, String notificationToken, Platform platform) {
    System.out.println("âž¡ï¸ Verifying Apple identity token");
    JWTClaimsSet claims = verifyIdentityToken(identityToken);
    String sub = claims.getSubject();
    String email = (String) claims.getClaim("email");
    return loginOrSignup(sub, email, fullName, notificationToken, platform);
  }

  private JWTClaimsSet verifyIdentityToken(String identityToken) {
    try {
      JWKSource<SecurityContext> keySource = new RemoteJWKSet<>(new URL(APPLE_JWKS_URL));
      ConfigurableJWTProcessor<SecurityContext> processor = new DefaultJWTProcessor<>();
      processor.setJWSKeySelector(new JWSVerificationKeySelector<>(JWSAlgorithm.RS256, keySource));

      JWTClaimsSet claims = processor.process(identityToken, null);

      if (!APPLE_ISSUER.equals(claims.getIssuer())) {
        throw new RuntimeException("Invalid Apple token issuer: " + claims.getIssuer());
      }
      if (!claims.getAudience().contains(bundleId)) {
        throw new RuntimeException(
            "Invalid Apple token audience: " + claims.getAudience() + ", expected: " + bundleId);
      }

      System.out.println("âœ… Apple identity token verified");
      return claims;

    } catch (RuntimeException e) {
      throw e;
    } catch (Exception e) {
      throw new RuntimeException("Apple identity token verification failed", e);
    }
  }

  private AuthTokenResponseData loginOrSignup(
      String providerUserId,
      String email,
      String fullName,
      String notificationToken,
      Platform platform) {

    System.out.println("ðŸ‘¤ Apple userId: " + providerUserId);

    Optional<UserAuthProviderEntity> existingProvider =
        userAuthProviderRepository.findByProviderTypeAndProviderUserId(
            PROVIDER_TYPE, providerUserId);

    UserEntity user;
    UserAuthProviderEntity provider;

    if (existingProvider.isPresent()) {
      System.out.println("ðŸ” Existing Apple user found");
      provider = existingProvider.get();
      user = provider.getUser();
    } else {
      System.out.println("ðŸ†• Creating new Apple user");

      String baseUsername =
          usernameUtil.normalizeUsername(
              fullName != null ? fullName : (email != null ? email.split("@")[0] : "appleuser"));

      String uniqueUsername = usernameUtil.generateUniqueUsername(baseUsername);

      user =
          userRepository.save(UserEntity.builder().username(uniqueUsername).locale("en").build());

      System.out.println("âœ… User saved | ID: " + user.getId());

      provider =
          userAuthProviderRepository.save(
              UserAuthProviderEntity.builder()
                  .user(user)
                  .providerType(PROVIDER_TYPE)
                  .providerUserId(providerUserId)
                  .email(email)
                  .build());

      System.out.println("âœ… Provider saved | ID: " + provider.getId());
    }

    return createSession(user, provider, notificationToken, platform);
  }

  private AuthTokenResponseData createSession(
      UserEntity user,
      UserAuthProviderEntity provider,
      String notificationToken,
      Platform platform) {
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
                .isActive(true)
                .build());

    System.out.println("âœ… Session created | ID: " + session.getId());

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
        .build();
  }
}
