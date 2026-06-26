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

  @Autowired private WebTestClient webTestClient;

  @Test
  void shouldReturnIssuesResponse() throws Exception {
    // ONHOLD issues are excluded from the public feed; only OPEN/PENDING/RESOLVED ones appear.
    // Test data has 4 issues: 3 OPEN (IDs 1, 2, 4) + 1 ONHOLD (ID 3).
    webTestClient
        .get()
        .uri(ISSUES_API_URL)
        .exchange()
        .expectStatus()
        .isOk()
        .expectBody()
        .jsonPath("$.data.issues.length()")
        .isEqualTo(3)
        .jsonPath("$.data.issues[0].user.username")
        .isEqualTo("john_doe")
        .jsonPath("$.data.issues[0].vote_count")
        .isEqualTo(0)
        .jsonPath("$.data.issues[0].verify_count")
        .isEqualTo(0);
  }

  @Test
  void shouldExcludeOnholdIssuesFromPublicFeed() throws Exception {
    // ONHOLD issues must not appear in the public /issues feed.
    // Test data: issue 4 (OPEN, Jaipur), issue 3 (ONHOLD — excluded), issue 2 (OPEN), issue 1
    // (OPEN).
    webTestClient
        .get()
        .uri(ISSUES_API_URL)
        .exchange()
        .expectStatus()
        .isOk()
        .expectBody()
        .jsonPath("$.data.issues.length()")
        .isEqualTo(3)
        // First should be issue 4 (newest OPEN, Jaipur)
        .jsonPath("$.data.issues[0].id")
        .isEqualTo(4)
        .jsonPath("$.data.issues[0].status")
        .isEqualTo("OPEN")
        // Second should be issue 2
        .jsonPath("$.data.issues[1].id")
        .isEqualTo(2)
        .jsonPath("$.data.issues[1].status")
        .isEqualTo("OPEN")
        // Third should be issue 1 (oldest)
        .jsonPath("$.data.issues[2].id")
        .isEqualTo(1)
        .jsonPath("$.data.issues[2].status")
        .isEqualTo("OPEN");
  }

  @Test
  void shouldReturnIssuesInReverseChronologicalOrder() throws Exception {
    // ONHOLD issues are excluded; only 3 OPEN issues should appear, newest first.
    webTestClient
        .get()
        .uri(ISSUES_API_URL)
        .exchange()
        .expectStatus()
        .isOk()
        .expectBody()
        .jsonPath("$.data.issues.length()")
        .isEqualTo(3)
        .jsonPath("$.data.issues[0].id")
        .isEqualTo(4)
        .jsonPath("$.data.issues[0].created_at")
        .isNotEmpty()
        .jsonPath("$.data.issues[1].id")
        .isEqualTo(2)
        .jsonPath("$.data.issues[1].created_at")
        .isNotEmpty()
        .jsonPath("$.data.issues[2].id")
        .isEqualTo(1)
        .jsonPath("$.data.issues[2].created_at")
        .isNotEmpty();
  }

  @Test
  void shouldFilterIssuesByLocalityHashtag() throws Exception {
    // This test assumes test data includes at least one issue with locality.hashtag
    // = "Jaipur"
    webTestClient
        .get()
        .uri(ISSUES_API_URL + "?locality=Jaipur")
        .exchange()
        .expectStatus()
        .isOk()
        .expectBody()
        .jsonPath("$.data.issues.length()")
        .isEqualTo(1)
        .jsonPath("$.data.issues[0].location.locality.hashtags[0]")
        .isEqualTo("Jaipur");
  }
}
