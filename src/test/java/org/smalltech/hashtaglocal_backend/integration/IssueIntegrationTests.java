package org.smalltech.hashtaglocal_backend.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.InputStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class IssueIntegrationTests {
	private final ObjectMapper objectMapper = new ObjectMapper();
	private JsonNode expectedJson;

	@BeforeEach
	public void setUpFixture() throws Exception {
		InputStream fixtureStream = getClass().getClassLoader().getResourceAsStream("issue-response.json");

		expectedJson = objectMapper.readTree(fixtureStream);

	}

	@Autowired
	private TestRestTemplate restTemplate;

	@Test
	void shouldReturnIssueResponse() throws Exception {
		ResponseEntity<String> response = restTemplate.getForEntity("/api/v1/issue/1", String.class);

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);

		JsonNode actualJson = objectMapper.readTree(response.getBody());
		assertEquals(expectedJson, actualJson);
	}
}
