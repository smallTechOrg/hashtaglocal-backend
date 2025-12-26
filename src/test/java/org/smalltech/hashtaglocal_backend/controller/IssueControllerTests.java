package org.smalltech.hashtaglocal_backend.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.smalltech.hashtaglocal_backend.model.APIResponse;
import org.smalltech.hashtaglocal_backend.model.Issue;
import org.smalltech.hashtaglocal_backend.model.Locality;
import org.smalltech.hashtaglocal_backend.model.Location;
import org.smalltech.hashtaglocal_backend.model.Media;
import org.smalltech.hashtaglocal_backend.model.ResponseData;
import org.smalltech.hashtaglocal_backend.model.User;
import org.smalltech.hashtaglocal_backend.model.ViewerContext;

class IssueControllerTests {

	@Test
	void getIssue_shouldReturnValidApiResponse() {
		// Arrange
		IssueController controller = new IssueController();

		// Act
		APIResponse actualResponse = controller.getIssue();
		APIResponse expectedResponse = expectedMockResponse();

		// Assert
		assertEquals(expectedResponse, actualResponse);
	}
	private APIResponse expectedMockResponse() {
		User user = new User("john_doe", "https://example.com/profile.jpg");
		Locality locality = new Locality(List.of("#Jaipur"));
		Location location;
		location = new Location("12.34", "56.78", locality, "Main Street", "Near City Mall");
		Media media = new Media(location, "photo", "https://example.com/image.jpg");
		Issue issue = new Issue(user, location, "road", "Large pothole causing traffic issues", "2025-12-26T18:00:00",
				List.of(media), 42, 10, "OPEN", 1);
		ViewerContext viewerContext = new ViewerContext(true);
		ResponseData data = new ResponseData(issue, viewerContext);

		return new APIResponse(data);
	}

}
