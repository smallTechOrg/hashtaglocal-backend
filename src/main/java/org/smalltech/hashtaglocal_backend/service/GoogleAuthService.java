package org.smalltech.hashtaglocal_backend.service;

import com.google.api.client.googleapis.auth.oauth2.GoogleIdToken;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdTokenVerifier;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.jackson2.JacksonFactory;
import jakarta.transaction.Transactional;
import java.util.Collections;
import java.util.Map;
import java.util.Optional;
import org.smalltech.hashtaglocal_backend.entity.UserAuthProviderEntity;
import org.smalltech.hashtaglocal_backend.entity.UserAuthSessionEntity;
import org.smalltech.hashtaglocal_backend.entity.UserEntity;
import org.smalltech.hashtaglocal_backend.model.GoogleUserResponse;
import org.smalltech.hashtaglocal_backend.model.TokenResponse;
import org.smalltech.hashtaglocal_backend.model.response.AuthTokenResponseData;
import org.smalltech.hashtaglocal_backend.repository.UserAuthProviderRepository;
import org.smalltech.hashtaglocal_backend.repository.UserAuthSessionRepository;
import org.smalltech.hashtaglocal_backend.repository.UserRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;

@Service
@Transactional
public class GoogleAuthService {

	@Value("${google.oauth.client-id}")
	private String clientId;

	@Value("${google.oauth.client-secret}")
	private String clientSecret;

	@Value("${google.oauth.redirect-uri}")
	private String redirectUri;

	private static final String TOKEN_URL = "https://oauth2.googleapis.com/token";
	private static final String USERINFO_URL = "https://www.googleapis.com/oauth2/v2/userinfo";

	private final UserRepository userRepository;
	private final UserAuthProviderRepository userAuthProviderRepository;
	private final UserAuthSessionRepository userAuthSessionRepository;
	private final TokenService tokenService;

	public GoogleAuthService(UserRepository userRepository, UserAuthProviderRepository userAuthProviderRepository,
			UserAuthSessionRepository userAuthSessionRepository, TokenService tokenService) {

		this.userRepository = userRepository;
		this.userAuthProviderRepository = userAuthProviderRepository;
		this.userAuthSessionRepository = userAuthSessionRepository;
		this.tokenService = tokenService;
	}

	/*
	 * =============================== AUTH CODE FLOW
	 * ===============================
	 */

	public AuthTokenResponseData handleAuthorizationCode(String code, String codeVerifier) {

		System.out.println("🔁 Exchanging auth code for Google tokens");

		RestTemplate restTemplate = new RestTemplate();

		MultiValueMap<String, String> body = new LinkedMultiValueMap<>();
		body.add("code", code);
		body.add("client_id", clientId);
		body.add("client_secret", clientSecret);
		body.add("redirect_uri", redirectUri);
		body.add("grant_type", "authorization_code");

		if (codeVerifier != null && !codeVerifier.isEmpty()) {
			body.add("code_verifier", codeVerifier);
		}

		@SuppressWarnings("unchecked")
		Map<String, Object> tokenResponse = restTemplate.postForObject(TOKEN_URL, body, Map.class);

		String idTokenString = tokenResponse.get("id_token").toString();

		System.out.println("✅ Google ID Token received");

		GoogleIdToken.Payload payload = verifyIdToken(idTokenString);

		return loginOrSignup(payload.getSubject(), payload.getEmail(), (String) payload.get("picture"),
				(String) payload.get("name"));
	}

	/*
	 * =============================== ACCESS TOKEN FLOW
	 * ===============================
	 */

	public AuthTokenResponseData handleAccessToken(String accessToken) {

		System.out.println("🔁 Fetching Google user info");

		RestTemplate restTemplate = new RestTemplate();

		GoogleUserResponse googleUser = restTemplate.getForObject(USERINFO_URL + "?access_token=" + accessToken,
				GoogleUserResponse.class);

		return loginOrSignup(googleUser.getId(), googleUser.getEmail(), googleUser.getPicture(), googleUser.getName());
	}

	/*
	 * =============================== LOGIN / SIGNUP
	 * ===============================
	 */

	private AuthTokenResponseData loginOrSignup(String providerUserId, String email, String picture, String name) {

		System.out.println("👤 Google userId: " + providerUserId);

		Optional<UserAuthProviderEntity> existingProvider = userAuthProviderRepository
				.findByProviderTypeAndProviderUserId("google", providerUserId);

		UserEntity user;
		UserAuthProviderEntity provider;

		if (existingProvider.isPresent()) {
			System.out.println("🔁 Existing user found");

			provider = existingProvider.get();
			user = provider.getUser();
		} else {
			System.out.println("🆕 Creating new user");

			String baseUsername = normalizeUsername(
					name != null ? name : (email != null ? email.split("@")[0] : "google"));

			String uniqueUsername = generateUniqueUsername(baseUsername);

			user = userRepository
					.save(UserEntity.builder().username(uniqueUsername).locale("en").profilePicture(picture).build());

			System.out.println("✅ User saved | ID: " + user.getId());

			provider = userAuthProviderRepository.save(UserAuthProviderEntity.builder().user(user)
					.providerType("google").providerUserId(providerUserId).email(email).build());

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

		UserAuthSessionEntity session = userAuthSessionRepository
				.save(UserAuthSessionEntity.builder().user(user).userAuthProvider(provider).accessToken(accessToken)
						.refreshToken(refreshToken).accessTokenExpiryTs(tokenService.accessExpiryEpochSeconds())
						.refreshTokenExpiryTs(tokenService.refreshExpiryEpochSeconds()).isActive(true).build());

		System.out.println("✅ Session created | ID: " + session.getId());

		return AuthTokenResponseData.builder()
				.accessToken(
						TokenResponse.builder().value(accessToken).expiry(session.getAccessTokenExpiryTs()).build())
				.refreshToken(
						TokenResponse.builder().value(refreshToken).expiry(session.getRefreshTokenExpiryTs()).build())
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

	/*
	 * =============================== ID TOKEN VERIFICATION
	 * ===============================
	 */

	private GoogleIdToken.Payload verifyIdToken(String idTokenString) {

		try {
			GoogleIdTokenVerifier verifier = new GoogleIdTokenVerifier.Builder(new NetHttpTransport(),
					JacksonFactory.getDefaultInstance()).setAudience(Collections.singletonList(clientId)).build();

			GoogleIdToken idToken = verifier.verify(idTokenString);

			if (idToken == null) {
				throw new RuntimeException("Invalid ID token");
			}

			System.out.println("✅ ID token verified");

			return idToken.getPayload();

		} catch (Exception e) {
			throw new RuntimeException("ID token verification failed", e);
		}
	}
}
