package org.smalltech.hashtaglocal_backend.controller;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;
import org.smalltech.hashtaglocal_backend.model.APIResponse;
import org.smalltech.hashtaglocal_backend.model.Issue;
import org.smalltech.hashtaglocal_backend.model.ResponseData;
import org.smalltech.hashtaglocal_backend.model.ViewerContext;

class IssueControllerTests {

    @Test
    void getIssue_shouldReturnValidApiResponse() {
        // Arrange
        IssueController controller = new IssueController();

        // Act
        APIResponse response = controller.getIssue();

        // Assert
        assertNotNull(response);

        ResponseData data = response.getData();
        assertNotNull(data);

        Issue issue = data.getIssue();
        assertNotNull(issue);

        assertEquals("road", issue.getType());
        assertEquals("OPEN", issue.getStatus());
        assertEquals(42, issue.getVoteCount());
        assertEquals(10, issue.getVerifyCount());

        assertNotNull(issue.getUser());
        assertEquals("john_doe", issue.getUser().getUsername());

        assertNotNull(issue.getLocation());
        assertEquals("Main Street", issue.getLocation().getColloquialName());

        ViewerContext viewerContext = data.getViewerContext();
        assertTrue(viewerContext.isUpvote());
    }
}
