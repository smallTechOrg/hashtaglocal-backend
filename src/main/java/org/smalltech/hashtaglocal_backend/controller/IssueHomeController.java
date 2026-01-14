package org.smalltech.hashtaglocal_backend.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import org.smalltech.hashtaglocal_backend.model.APIResponse;
import org.smalltech.hashtaglocal_backend.model.Issue;
import org.smalltech.hashtaglocal_backend.model.Locality;
import org.smalltech.hashtaglocal_backend.model.Location;
import org.smalltech.hashtaglocal_backend.model.Media;
import org.smalltech.hashtaglocal_backend.model.ResponseData;
import org.smalltech.hashtaglocal_backend.model.User;
import org.smalltech.hashtaglocal_backend.model.ViewerContext;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/issues")
@Tag(name = "Issues Home", description = "issue home API")

public class IssueHomeController {

	@GetMapping
	@Operation(summary = "Get issue Home", description = "Returns a List of issues with user, location, locality and viewer context.")
	@ApiResponse(responseCode = "200", description = "Successful issue response", content = @Content(mediaType = "application/json", schema = @Schema(implementation = APIResponse.class)))
	public APIResponse getIssues() {
		return getMockResponse();
	}

	private APIResponse getMockResponse() {

		User user = User.builder().username("john_doe").profilePhoto("https://example.com/profile.jpg").build();

		Locality locality = Locality.builder().hashtags(List.of("#Jaipur")).build();

		Location location = Location.builder().lat("12.34").lng("56.78").locality(locality)
				.address("Sector 3, Jawahar Nagar").colloquialName("Near Patrika Gate").build();

		Media media1 = Media.builder().location(location).type("photo")
				.url("https://sripath.com/wp-content/uploads/2025/01/iStock-174662203.jpg").build();

		Media media2 = Media.builder().location(location).type("photo")
				.url("https://nub.news/api/image/526263/article.png").build();

		ViewerContext viewerContext = ViewerContext.builder().upvote(true).build();

		Issue issue1 = Issue.builder().id(1L).user(user).location(location).type("pothole")
				.description("Large pothole causing traffic issues").createdAt("2025-12-26T18:00:00")
				.mediaUrls(List.of(media1, media2)).voteCount(42).verifyCount(10).status("OPEN").rank(1)
				.viewerContext(viewerContext).build();

		Issue issue2 = Issue.builder().id(2L).user(user).location(location).type("pothole")
				.description("Large pothole causing traffic issues").createdAt("2025-12-26T18:00:00")
				.mediaUrls(List.of(media1, media2)).voteCount(42).verifyCount(10).status("OPEN").rank(1)
				.viewerContext(viewerContext).build();

		ResponseData data = ResponseData.builder().issues(List.of(issue1, issue2)).build();

		return APIResponse.builder().data(data).build();
	}

}
