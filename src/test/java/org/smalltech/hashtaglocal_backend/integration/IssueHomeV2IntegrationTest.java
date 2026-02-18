package org.smalltech.hashtaglocal_backend.integration;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class IssueHomeV2IntegrationTest {

	@Autowired
	private MockMvc mockMvc;

	@Test
	void getIssuesNearby_shouldExecutePostGISQuery() throws Exception {
		// This will actually execute the native PostGIS query against your local
		// PostgreSQL
		// It would have caught the @EntityGraph error immediately
		mockMvc.perform(get("/api/v2/issues").param("lat", "26.9124").param("lng", "75.8073"))
				.andExpect(status().isOk()).andExpect(jsonPath("$.data").exists())
				.andExpect(jsonPath("$.data.issues").isArray());
	}

	@Test
	void getIssuesNearby_shouldRequireLatParameter() throws Exception {
		mockMvc.perform(get("/api/v2/issues").param("lng", "75.8073")).andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.error").exists());
	}

	@Test
	void getIssuesNearby_shouldRequireLngParameter() throws Exception {
		mockMvc.perform(get("/api/v2/issues").param("lat", "26.9124")).andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.error").exists());
	}
}
