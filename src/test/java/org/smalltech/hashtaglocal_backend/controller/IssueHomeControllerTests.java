package org.smalltech.hashtaglocal_backend.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.LocalDateTime;
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

class IssueHomeControllerTests {
	@Test
	void getIssues_shouldReturnValidApiResponse() {
		// Arrange
		IssueHomeController controller = new IssueHomeController();

		// Act
		APIResponse actualResponse = controller.getIssues();
		APIResponse expectedResponse = expectedMockResponse();

		// Assert
		assertEquals(expectedResponse, actualResponse);
	}

	private APIResponse expectedMockResponse() {

		User user = User.builder().username("john_doe").profilePhoto("https://example.com/profile.jpg").build();

		Locality locality = Locality.builder().hashtags(List.of("#Jaipur")).build();

		Location location = Location.builder().lat(12.34).lng(56.78).locality(locality)
				.address("Sector 3, Jawahar Nagar").colloquialName("Near Patrika Gate").build();

		Media media1 = Media.builder().location(location).type("photo")
				.url("https://sripath.com/wp-content/uploads/2025/01/iStock-174662203.jpg").build();

		Media media2 = Media.builder().location(location).type("photo")
				.url("https://nub.news/api/image/526263/article.png").build();

		ViewerContext viewerContext = ViewerContext.builder().upvote(true).build();

		LocalDateTime createdDateTime = LocalDateTime.of(2025, 12, 26, 18, 0, 0);

		Issue issue1 = Issue.builder().id(1L).user(user).location(location).type("pothole")
				.description("Large pothole causing traffic issues").createdAt(createdDateTime)
				.mediaUrls(List.of(media1, media2)).voteCount(42).verifyCount(10).status("OPEN").rank(1)
				.viewerContext(viewerContext).build();

		Issue issue2 = Issue.builder().id(2L).user(user).location(location).type("pothole")
				.description("Large pothole causing traffic issues").createdAt(createdDateTime)
				.mediaUrls(List.of(media1, media2)).voteCount(42).verifyCount(10).status("OPEN").rank(1)
				.viewerContext(viewerContext).build();

		ResponseData data = ResponseData.builder().issues(List.of(issue1, issue2)).build();

		return APIResponse.builder().data(data).build();
	}

}
