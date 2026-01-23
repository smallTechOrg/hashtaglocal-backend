package org.smalltech.hashtaglocal_backend.integration;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.reactive.server.WebTestClient;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@Import(IssueTestDataConfig.class)
class IssueHomeIntegrationTests {
	private static final String ISSUES_API_URL = "/api/v1/issues";

	@Autowired
	private WebTestClient webTestClient;

	@Test
	void shouldReturnIssuesResponse() throws Exception {
		webTestClient.get().uri(ISSUES_API_URL).exchange().expectStatus().isOk().expectBody()
				.jsonPath("$.data.issues.length()").isEqualTo(2).jsonPath("$.data.issues[0].id").isEqualTo(1)
				.jsonPath("$.data.issues[0].user.username").isEqualTo("john_doe").jsonPath("$.data.issues[0].type")
				.isEqualTo("pothole").jsonPath("$.data.issues[0].description")
				.isEqualTo("Large pothole causing traffic issues").jsonPath("$.data.issues[0].status").isEqualTo("OPEN")
				.jsonPath("$.data.issues[0].media_urls.length()").isEqualTo(2).jsonPath("$.data.issues[0].vote_count")
				.isEqualTo(0).jsonPath("$.data.issues[0].verify_count").isEqualTo(0).jsonPath("$.data.issues[1].id")
				.isEqualTo(2);
	}
}
