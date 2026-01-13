package org.smalltech.hashtaglocal_backend.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.InputStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.reactive.server.WebTestClient;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class IssueIntegrationTests {
	private static final String ISSUE_RESPONSE_FIXTURE = "issue-response.json";
	private static final String ISSUE_API_URL = "/api/v1/issue/1";
	private final ObjectMapper objectMapper = new ObjectMapper();
	private JsonNode expectedJson;

	@BeforeEach
	public void setUpFixture() throws Exception {
		InputStream fixtureStream = getClass().getClassLoader().getResourceAsStream(ISSUE_RESPONSE_FIXTURE);
		expectedJson = objectMapper.readTree(fixtureStream);
	}

	@Autowired
	private WebTestClient webTestClient;

	@Test
	void shouldReturnIssueResponse() throws Exception {
		webTestClient.get().uri(ISSUE_API_URL).exchange().expectStatus().isOk().expectBody()
				.json(expectedJson.toString());
	}
}
