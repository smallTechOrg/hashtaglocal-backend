package org.smalltech.hashtaglocal_backend.integration;

import org.junit.jupiter.api.Test;
import org.smalltech.hashtaglocal_backend.entity.UserAuthProviderEntity;
import org.smalltech.hashtaglocal_backend.entity.UserAuthSessionEntity;
import org.smalltech.hashtaglocal_backend.entity.UserEntity;
import org.smalltech.hashtaglocal_backend.repository.UserAuthProviderRepository;
import org.smalltech.hashtaglocal_backend.repository.UserAuthSessionRepository;
import org.smalltech.hashtaglocal_backend.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.reactive.server.WebTestClient;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class MediaIntegrationTests {

	private static final String SIGNED_URL_API = "/api/v1/media/upload-url?content_type=image/jpeg";

	@Autowired
	private WebTestClient webTestClient;

	@Autowired
	private UserRepository userRepository;

	@Autowired
	private UserAuthSessionRepository userAuthSessionRepository;

	@Autowired
	private UserAuthProviderRepository userAuthProviderRepository;

	private String createAuthenticatedToken() {
		// Create or get user with unique username per test run
		String username = "test-media-user-" + System.currentTimeMillis();
		UserEntity user = userRepository.save(UserEntity.builder().username(username)
				.profilePicture("https://example.com/pic.jpg").locale("en_US").build());

		// Create auth provider
		UserAuthProviderEntity provider = userAuthProviderRepository
				.save(UserAuthProviderEntity.builder().user(user).providerType("TEST").providerUserId(username)
						.email("testmedia-" + System.currentTimeMillis() + "@example.com").build());

		// Create auth session with valid token
		String token = "media-test-token-" + System.currentTimeMillis();
		long expiryTime = System.currentTimeMillis() / 1000 + 3600; // 1 hour from now
		UserAuthSessionEntity session = UserAuthSessionEntity.builder().user(user).userAuthProvider(provider)
				.accessToken(token).accessTokenExpiryTs(expiryTime).refreshToken("refresh-" + token)
				.refreshTokenExpiryTs(expiryTime + 7200).isActive(true).build();
		userAuthSessionRepository.saveAndFlush(session);

		return token;
	}

	@Test
	void shouldReturnValidSignedUploadUrlResponse() {
		// Create authenticated token
		String token = createAuthenticatedToken();

		webTestClient.get().uri(SIGNED_URL_API).header("Authorization", "Bearer " + token).exchange().expectStatus()
				.isOk().expectBody()

				// --- structure assertions ---
				.jsonPath("$.data").exists().jsonPath("$.data.media_url").exists()
				.jsonPath("$.data.media_url.signed_url").exists().jsonPath("$.data.media_url.path").exists();
	}
}
