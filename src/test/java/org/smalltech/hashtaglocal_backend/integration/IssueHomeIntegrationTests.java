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
class IssueHomeIntegrationTests {
	private static final String ISSUES_RESPONSE_FIXTURE = "issues-response.json";
	private static final String ISSUES_API_URL = "/api/v1/issues";
	private final ObjectMapper objectMapper = new ObjectMapper();
	private JsonNode expectedJson;

	@BeforeEach
	public void setUpFixture() throws Exception {
		InputStream fixtureStream = getClass().getClassLoader().getResourceAsStream(ISSUES_RESPONSE_FIXTURE);
		expectedJson = objectMapper.readTree(fixtureStream);
	}

	@Autowired
	private WebTestClient webTestClient;

	@Test
	void shouldReturnIssueResponse() throws Exception {
		webTestClient.get().uri(ISSUES_API_URL).exchange().expectStatus().isOk().expectBody()
				.json(expectedJson.toString());
	}
}
