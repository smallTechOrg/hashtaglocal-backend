package org.smalltech.hashtaglocal_backend.integration;

import static org.hamcrest.Matchers.*;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.reactive.server.WebTestClient;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@Import(IssueTestDataConfig.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@Disabled(
    "Test has flaky data setup issues when run with other tests. Similar coverage exists in other integration tests.")
class IssueIntegrationTests {
  private static final String ISSUE_API_URL = "/api/v1/issue/1";

  @Autowired private WebTestClient webTestClient;

  @Test
  void shouldReturnIssueResponse() throws Exception {
    webTestClient
        .get()
        .uri(ISSUE_API_URL)
        .exchange()
        .expectStatus()
        .isOk()
        .expectBody()
        .jsonPath("$.data.issue.id")
        .isEqualTo(1)
        .jsonPath("$.data.issue.user.username")
        .isEqualTo("john_doe")
        .jsonPath("$.data.issue.type")
        .isEqualTo("pothole")
        .jsonPath("$.data.issue.description")
        .isEqualTo("Large pothole causing traffic issues")
        .jsonPath("$.data.issue.status")
        .isEqualTo("OPEN")
        .jsonPath("$.data.issue.media_urls.length()")
        .isEqualTo(2)
        .jsonPath("$.data.issue.vote_count")
        .isEqualTo(0)
        .jsonPath("$.data.issue.verify_count")
        .isEqualTo(0)
        .jsonPath("$.data.issue.viewer_context.upvote")
        .isEqualTo(false);
  }
}
