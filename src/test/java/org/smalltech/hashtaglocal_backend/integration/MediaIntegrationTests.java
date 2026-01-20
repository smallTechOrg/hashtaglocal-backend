package org.smalltech.hashtaglocal_backend.integration;

import org.junit.jupiter.api.Test;
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

	@Test
	void shouldReturnValidSignedUploadUrlResponse() {

		webTestClient.get().uri(SIGNED_URL_API).exchange().expectStatus().isOk().expectBody()

				// --- structure assertions ---
				.jsonPath("$.data").exists().jsonPath("$.data.media_url").exists()
				.jsonPath("$.data.media_url.signed_url").exists().jsonPath("$.data.media_url.path").exists()

				// --- invariant assertions ---
				.jsonPath("$.data.media_url.path").value(path -> ((String) path).startsWith("gs://hashtaglocalbucket/"))
				.jsonPath("$.data.media_url.path").value(path -> ((String) path).endsWith(".jpg"));
	}
}
