package org.smalltech.hashtaglocal_backend.service;

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
import org.smalltech.hashtaglocal_backend.model.TokenResponse;
import org.smalltech.hashtaglocal_backend.model.response.AuthTokenResponseData;
import org.smalltech.hashtaglocal_backend.repository.UserAuthProviderRepository;
import org.smalltech.hashtaglocal_backend.repository.UserAuthSessionRepository;
import org.smalltech.hashtaglocal_backend.repository.UserRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
@Transactional
public class AppleAuthService {

  private static final String APPLE_JWKS_URL = "https://appleid.apple.com/auth/keys";
  private static final String APPLE_ISSUER = "https://appleid.apple.com";

  @Value("${apple.bundle-id}")
  private String bundleId;

  private final UserRepository userRepository;
  private final UserAuthProviderRepository userAuthProviderRepository;
  private final UserAuthSessionRepository userAuthSessionRepository;
  private final TokenService tokenService;

  public AppleAuthService(
      UserRepository userRepository,
      UserAuthProviderRepository userAuthProviderRepository,
      UserAuthSessionRepository userAuthSessionRepository,
      TokenService tokenService) {
    this.userRepository = userRepository;
    this.userAuthProviderRepository = userAuthProviderRepository;
    this.userAuthSessionRepository = userAuthSessionRepository;
    this.tokenService = tokenService;
  }

  /*
   * =============================== ENTRY POINT
   * ===============================
   */

  public AuthTokenResponseData handleIdentityToken(String identityToken, String fullName) {
    System.out.println("➡️ Verifying Apple identity token");
    JWTClaimsSet claims = verifyIdentityToken(identityToken);
    String sub = claims.getSubject();
    String email = (String) claims.getClaim("email");
    return loginOrSignup(sub, email, fullName);
  }

  /*
   * =============================== TOKEN VERIFICATION
   * ===============================
   */

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

      System.out.println("✅ Apple identity token verified");
      return claims;

    } catch (RuntimeException e) {
      throw e;
    } catch (Exception e) {
      throw new RuntimeException("Apple identity token verification failed", e);
    }
  }

  /*
   * =============================== LOGIN / SIGNUP
   * ===============================
   */

  private AuthTokenResponseData loginOrSignup(
      String providerUserId, String email, String fullName) {

    System.out.println("👤 Apple userId: " + providerUserId);

    Optional<UserAuthProviderEntity> existingProvider =
        userAuthProviderRepository.findByProviderTypeAndProviderUserId("apple", providerUserId);

    UserEntity user;
    UserAuthProviderEntity provider;

    if (existingProvider.isPresent()) {
      System.out.println("🔁 Existing Apple user found");
      provider = existingProvider.get();
      user = provider.getUser();
    } else {
      System.out.println("🆕 Creating new Apple user");

      String baseUsername =
          normalizeUsername(
              fullName != null ? fullName : (email != null ? email.split("@")[0] : "appleuser"));

      String uniqueUsername = generateUniqueUsername(baseUsername);

      user =
          userRepository.save(UserEntity.builder().username(uniqueUsername).locale("en").build());

      System.out.println("✅ User saved | ID: " + user.getId());

      provider =
          userAuthProviderRepository.save(
              UserAuthProviderEntity.builder()
                  .user(user)
                  .providerType("apple")
                  .providerUserId(providerUserId)
                  .email(email)
                  .build());

      System.out.println("✅ Provider saved | ID: " + provider.getId());
    }

    return createSession(user, provider);
  }

  /*
   * =============================== SESSION + TOKENS
   * ===============================
   */

  private AuthTokenResponseData createSession(UserEntity user, UserAuthProviderEntity provider) {
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
                .isActive(true)
                .build());

    System.out.println("✅ Session created | ID: " + session.getId());

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

  /*
   * =============================== USERNAME HELPERS
   * ===============================
   */

  private String normalizeUsername(String name) {
    if (name == null || name.isBlank()) {
      return "user";
    }
    return name.toLowerCase().replaceAll("\\s+", "").replaceAll("[^a-z0-9]", "");
  }

  private String generateUniqueUsername(String baseUsername) {
    String username = baseUsername;
    int attempt = 0;
    while (userRepository.findByUsername(username).isPresent()) {
      int random = 100 + (int) (Math.random() * 900);
      username = baseUsername + random;
      attempt++;
      if (attempt > 3) {
        username = baseUsername + (System.currentTimeMillis() % 100000);
        break;
      }
    }
    return username;
  }
}
