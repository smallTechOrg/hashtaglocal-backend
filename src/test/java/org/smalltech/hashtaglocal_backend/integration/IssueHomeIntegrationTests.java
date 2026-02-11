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
				.jsonPath("$.data.issues.length()").isEqualTo(4).jsonPath("$.data.issues[0].user.username")
				.isEqualTo("john_doe").jsonPath("$.data.issues[0].vote_count").isEqualTo(0)
				.jsonPath("$.data.issues[0].verify_count").isEqualTo(0);
	}

	@Test
	void shouldReturnBothOpenAndOnholdIssues() throws Exception {
		// Should return 4 issues: issue 4 (OPEN, Jaipur, newest), issue 3 (ONHOLD),
		// issue 2 (OPEN), issue 1 (OPEN, oldest)
		webTestClient.get().uri(ISSUES_API_URL).exchange().expectStatus().isOk().expectBody()
				.jsonPath("$.data.issues.length()").isEqualTo(4)
				// First issue should be issue 4 (OPEN, Jaipur, newest - ID 4)
				.jsonPath("$.data.issues[0].id").isEqualTo(4).jsonPath("$.data.issues[0].status").isEqualTo("OPEN")
				.jsonPath("$.data.issues[0].created_at").isEqualTo("2025-12-28T09:00:00")
				// Second issue should be issue 3 (ONHOLD - ID 3)
				.jsonPath("$.data.issues[1].id").isEqualTo(3).jsonPath("$.data.issues[1].status").isEqualTo("ONHOLD")
				.jsonPath("$.data.issues[1].created_at").isEqualTo("2025-12-27T12:00:00")
				// Third issue should be issue 2 (OPEN - ID 2)
				.jsonPath("$.data.issues[2].id").isEqualTo(2).jsonPath("$.data.issues[2].status").isEqualTo("OPEN")
				.jsonPath("$.data.issues[2].created_at").isEqualTo("2025-12-26T18:00:00")
				// Fourth issue should be issue 1 (OPEN, oldest - ID 1)
				.jsonPath("$.data.issues[3].id").isEqualTo(1).jsonPath("$.data.issues[3].status").isEqualTo("OPEN")
				.jsonPath("$.data.issues[3].created_at").isEqualTo("2025-12-25T10:00:00");
	}

	@Test
	void shouldReturnIssuesInReverseChronologicalOrder() throws Exception {
		webTestClient.get().uri(ISSUES_API_URL).exchange().expectStatus().isOk().expectBody()
				.jsonPath("$.data.issues.length()").isEqualTo(4).jsonPath("$.data.issues[0].id").isEqualTo(4)
				.jsonPath("$.data.issues[0].created_at").isEqualTo("2025-12-28T09:00:00")
				.jsonPath("$.data.issues[1].id").isEqualTo(3).jsonPath("$.data.issues[1].created_at")
				.isEqualTo("2025-12-27T12:00:00").jsonPath("$.data.issues[2].id").isEqualTo(2)
				.jsonPath("$.data.issues[2].created_at").isEqualTo("2025-12-26T18:00:00")
				.jsonPath("$.data.issues[3].id").isEqualTo(1).jsonPath("$.data.issues[3].created_at")
				.isEqualTo("2025-12-25T10:00:00");
	}

	@Test
	void shouldFilterIssuesByLocalityHashtag() throws Exception {
		// This test assumes test data includes at least one issue with locality.hashtag
		// = "Jaipur"
		webTestClient.get().uri(ISSUES_API_URL + "?locality=Jaipur").exchange().expectStatus().isOk().expectBody()
				.jsonPath("$.data.issues.length()").isEqualTo(1)
				.jsonPath("$.data.issues[0].location.locality.hashtags[0]").isEqualTo("Jaipur");
	}
}
