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
		Location location = new Location("12.34", "56.78", locality, "Sector 3, Jawahar Nagar", "Near Patrika Gate");
		Media media1 = new Media(location, "photo",
				"https://sripath.com/wp-content/uploads/2025/01/iStock-174662203.jpg");
		Media media2 = new Media(location, "photo", "https://nub.news/api/image/526263/article.png");
		ViewerContext viewerContext = new ViewerContext(true);
		Issue issue = new Issue(user, location, "pothole", "Large pothole causing traffic issues",
				"2025-12-26T18:00:00", List.of(media1, media2), 42, 10, "OPEN", 1, viewerContext);
		ResponseData data = new ResponseData(issue);

		return new APIResponse(data);
	}

}
