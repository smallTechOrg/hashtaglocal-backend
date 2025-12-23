//Unit test for IssueResponse
package org.smalltech.hashtaglocal_backend.controller;

import org.junit.jupiter.api.Test;
import org.smalltech.hashtaglocal_backend.model.IssueResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(IssueController.class)
class IssueControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void getMockIssue_returnsExpectedJson() throws Exception {
        mockMvc.perform(get("/api/v1/issue/1")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                // top-level
                .andExpect(jsonPath("$.data").exists())
                // issue
                .andExpect(jsonPath("$.data.issue").exists())
                .andExpect(jsonPath("$.data.issue.user.username").isNotEmpty())
                .andExpect(jsonPath("$.data.issue.user.profileUrl").isNotEmpty())
                .andExpect(jsonPath("$.data.issue.location.lat").isNotEmpty())
                .andExpect(jsonPath("$.data.issue.location.lng").isNotEmpty())
                .andExpect(jsonPath("$.data.issue.location.locality.hashtags").isArray())
                .andExpect(jsonPath("$.data.issue.type").isNotEmpty())
                .andExpect(jsonPath("$.data.issue.description").isNotEmpty())
                .andExpect(jsonPath("$.data.issue.mediaUrls").isArray())
                .andExpect(jsonPath("$.data.issue.voteCount").isNotEmpty())
                .andExpect(jsonPath("$.data.issue.status").isNotEmpty())
                .andExpect(jsonPath("$.data.issue.verifyCount").isNotEmpty())
                .andExpect(jsonPath("$.data.issue.rank").isNotEmpty())
                // viewer_context
                .andExpect(jsonPath("$.data.viewerContext").exists())
                .andExpect(jsonPath("$.data.viewerContext.upvote").isNotEmpty());
    }
}
