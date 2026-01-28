package org.smalltech.hashtaglocal_backend.controller;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.util.Collections;
import java.util.Map;
import java.util.Optional;

import org.smalltech.hashtaglocal_backend.entity.UserAuthProviderEntity;
import org.smalltech.hashtaglocal_backend.entity.UserAuthSessionEntity;
import org.smalltech.hashtaglocal_backend.entity.UserEntity;
import org.smalltech.hashtaglocal_backend.model.AuthResponse;
import org.smalltech.hashtaglocal_backend.repository.UserAuthProviderRepository;
import org.smalltech.hashtaglocal_backend.repository.UserAuthSessionRepository;
import org.smalltech.hashtaglocal_backend.repository.UserRepository;
import org.smalltech.hashtaglocal_backend.service.TokenService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestTemplate;

import com.google.api.client.googleapis.auth.oauth2.GoogleIdToken;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdTokenVerifier;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.jackson2.JacksonFactory;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

@RestController
@RequestMapping("/auth/google")
@Tag(name = "Authentication", description = "Google OAuth callback APIs")
public class GoogleAuthCallbackController {

	@Value("${google.oauth.client-id}")
	private String clientId;

	@Value("${google.oauth.client-secret}")
	private String clientSecret;

	@Value("${google.oauth.redirect-uri}")
	private String redirectUri;

	private static final String TOKEN_URL = "https://oauth2.googleapis.com/token";

	private final UserRepository userRepository;
	private final UserAuthProviderRepository userAuthProviderRepository;
	private final UserAuthSessionRepository userAuthSessionRepository;
	private final TokenService tokenService;

	public GoogleAuthCallbackController(UserRepository userRepository,
			UserAuthProviderRepository userAuthProviderRepository,
			UserAuthSessionRepository userAuthSessionRepository, TokenService tokenService) {
		this.userRepository = userRepository;
		this.userAuthProviderRepository = userAuthProviderRepository;
		this.userAuthSessionRepository = userAuthSessionRepository;
		this.tokenService = tokenService;
	}

	@GetMapping("/callback")
	@Operation(summary = "Google OAuth callback")
	public ResponseEntity<?> googleCallback(@RequestParam("code") String code) throws IOException {

		// 1️⃣ Exchange code → tokens
		RestTemplate restTemplate = new RestTemplate();

		MultiValueMap<String, String> body = new LinkedMultiValueMap<>();
		body.add("code", code);
		body.add("client_id", clientId);
		body.add("client_secret", clientSecret);
		body.add("redirect_uri", redirectUri);
		body.add("grant_type", "authorization_code");

		@SuppressWarnings("unchecked")
		Map<String, Object> tokenResponse = restTemplate.postForObject(TOKEN_URL, body, Map.class);

		if (tokenResponse == null || tokenResponse.get("id_token") == null) {
			return ResponseEntity.badRequest().body("Failed to get ID token from Google");
		}

		String idTokenString = tokenResponse.get("id_token").toString();

		// 2️⃣ Verify ID token
		GoogleIdTokenVerifier verifier = new GoogleIdTokenVerifier.Builder(new NetHttpTransport(),
				JacksonFactory.getDefaultInstance()).setAudience(Collections.singletonList(clientId)).build();

		GoogleIdToken idToken;
		try {
			idToken = verifier.verify(idTokenString);
		} catch (GeneralSecurityException e) {
			return ResponseEntity.status(500).body("Failed to verify ID token: " + e.getMessage());
		}

		if (idToken == null) {
			return ResponseEntity.status(401).body("Invalid ID token");
		}

		GoogleIdToken.Payload payload = idToken.getPayload();

		// 3️⃣ Extract user info
		String googleUserId = payload.getSubject();
		String email = payload.getEmail();
		String name = (String) payload.get("name");
		String picture = (String) payload.get("picture");

		// 4️⃣ Find or create provider + user
		String providerType = "google";
		Optional<UserAuthProviderEntity> existingProvider = userAuthProviderRepository
			.findByProviderTypeAndProviderUserId(providerType, googleUserId);

		UserAuthProviderEntity providerEntity;
		UserEntity user;

		if (existingProvider.isPresent()) {
		    providerEntity = existingProvider.get();
		    user = providerEntity.getUser();
		} else {
		    // create new user (minimal) and provider
		    user = UserEntity.builder().username(email != null ? email : "user-" + googleUserId)
			    .locale("en").profilePicture(picture).build();
		    user = userRepository.save(user);

		    providerEntity = UserAuthProviderEntity.builder().user(user).providerType(providerType)
			    .providerUserId(googleUserId).email(email).build();
		    providerEntity = userAuthProviderRepository.save(providerEntity);
		}

		// 5️⃣ Create session + tokens
		String accessToken = tokenService.generateToken();
		String refreshToken = tokenService.generateToken();
		long accessExpiry = tokenService.accessExpiryEpochSeconds();
		long refreshExpiry = tokenService.refreshExpiryEpochSeconds();

		UserAuthSessionEntity session = UserAuthSessionEntity.builder().userAuthProvider(providerEntity)
			.deviceId(null).user(user).accessToken(accessToken).accessTokenExpiryTs(accessExpiry)
			.refreshToken(refreshToken).refreshTokenExpiryTs(refreshExpiry).isActive(true).build();

		session = userAuthSessionRepository.save(session);

		AuthResponse authResponse = new AuthResponse(accessToken, accessExpiry, refreshToken, refreshExpiry,
			user.getId(), providerEntity.getId());

		return ResponseEntity.ok(authResponse);
	}
}
