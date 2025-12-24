package org.smalltech.hashtaglocal_backend.integration;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class IssueIntegrationTests {

	@Autowired
	private TestRestTemplate restTemplate;

	@Test
	void shouldReturnIssueResponse() {
		ResponseEntity<String> response = restTemplate.getForEntity("/api/v1/issue/1", String.class);

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
		assertThat(response.getBody()).contains("verifyCount");
		assertThat(response.getBody()).contains("mediaUrls");
	}
}
