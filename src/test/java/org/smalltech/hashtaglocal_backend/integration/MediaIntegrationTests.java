package org.smalltech.hashtaglocal_backend.integration;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import com.google.cloud.storage.BlobInfo;
import com.google.cloud.storage.Storage;
import java.net.URL;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.smalltech.hashtaglocal_backend.entity.UserAuthProviderEntity;
import org.smalltech.hashtaglocal_backend.entity.UserAuthSessionEntity;
import org.smalltech.hashtaglocal_backend.entity.UserEntity;
import org.smalltech.hashtaglocal_backend.repository.UserAuthProviderRepository;
import org.smalltech.hashtaglocal_backend.repository.UserAuthSessionRepository;
import org.smalltech.hashtaglocal_backend.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.reactive.server.WebTestClient;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class MediaIntegrationTests {

  private static final String SIGNED_URL_API = "/api/v1/media/upload-url?content_type=image/jpeg";

  @Autowired private WebTestClient webTestClient;

  @Autowired private UserRepository userRepository;

  @Autowired private UserAuthSessionRepository userAuthSessionRepository;

  @Autowired private UserAuthProviderRepository userAuthProviderRepository;

  @MockBean private Storage storage;

  private String createAuthenticatedToken() {
    String username = "test-media-user-" + System.currentTimeMillis();

    UserEntity user =
        userRepository.save(
            UserEntity.builder()
                .username(username)
                .profilePicture("https://example.com/pic.jpg")
                .locale("en_US")
                .build());

    UserAuthProviderEntity provider =
        userAuthProviderRepository.save(
            UserAuthProviderEntity.builder()
                .user(user)
                .providerType("TEST")
                .providerUserId(username)
                .email("testmedia-" + System.currentTimeMillis() + "@example.com")
                .build());

    String token = "media-test-token-" + System.currentTimeMillis();
    long expiryTime = System.currentTimeMillis() / 1000 + 3600;

    UserAuthSessionEntity session =
        UserAuthSessionEntity.builder()
            .user(user)
            .userAuthProvider(provider)
            .accessToken(token)
            .accessTokenExpiryTs(expiryTime)
            .refreshToken("refresh-" + token)
            .refreshTokenExpiryTs(expiryTime + 7200)
            .isActive(true)
            .build();

    userAuthSessionRepository.saveAndFlush(session);

    return token;
  }

  @Test
  void shouldReturnValidSignedUploadUrlResponse() throws Exception {
    URL fakeUrl = new URL("https://signed.example.com/upload");

    when(storage.signUrl(
            any(BlobInfo.class),
            eq(15L),
            eq(TimeUnit.MINUTES),
            any(Storage.SignUrlOption.class),
            any(Storage.SignUrlOption.class)))
        .thenReturn(fakeUrl);

    String token = createAuthenticatedToken();

    webTestClient
        .get()
        .uri(SIGNED_URL_API)
        .header("Authorization", "Bearer " + token)
        .exchange()
        .expectStatus()
        .isOk()
        .expectBody()
        .jsonPath("$.data")
        .exists()
        .jsonPath("$.data.media_url")
        .exists()
        .jsonPath("$.data.media_url.signed_url")
        .isEqualTo(fakeUrl.toString())
        .jsonPath("$.data.media_url.path")
        .value(path -> ((String) path).startsWith("gs://hashtaglocalbucket/"));
  }

  @Test
  void shouldFailIfContentTypeMissing() {
    String token = createAuthenticatedToken();

    webTestClient
        .get()
        .uri("/api/v1/media/upload-url")
        .header("Authorization", "Bearer " + token)
        .exchange()
        .expectStatus()
        .isBadRequest();
  }
}
