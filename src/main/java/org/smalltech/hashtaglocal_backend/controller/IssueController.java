package org.smalltech.hashtaglocal_backend.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.LocalDateTime;
import java.util.List;
import org.smalltech.hashtaglocal_backend.model.APIResponse;
import org.smalltech.hashtaglocal_backend.model.Issue;
import org.smalltech.hashtaglocal_backend.model.Locality;
import org.smalltech.hashtaglocal_backend.model.Location;
import org.smalltech.hashtaglocal_backend.model.Media;
import org.smalltech.hashtaglocal_backend.model.MediaLocation;
import org.smalltech.hashtaglocal_backend.model.ResponseData;
import org.smalltech.hashtaglocal_backend.model.User;
import org.smalltech.hashtaglocal_backend.model.ViewerContext;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/issue")
@Tag(name = "Issue", description = "issue API")

public class IssueController {

	@GetMapping("/1")
	@Operation(summary = "Get issue", description = "Returns a issue response with user, location, locality and viewer context.")
	@ApiResponse(responseCode = "200", description = "Successful issue response", content = @Content(mediaType = "application/json", schema = @Schema(implementation = APIResponse.class)))
	public APIResponse getIssue() {
		return getMockResponse();

	}

	private APIResponse getMockResponse() {

		MediaLocation mediaLocation = new MediaLocation("12.34", "56.78");

		Media media = new Media(mediaLocation, "photo", "https://example.com/image.jpg");

		// ---- User ----
		User user = new User("john_doe", "https://example.com/profile.jpg");

		// ---- Locality ----
		Locality locality = new Locality(List.of("#Jaipur"));

		// ---- Location ----
		Location location = new Location("12.34", "56.78", locality, "Main Street", "Near City Mall");

		// ---- Issue ----
		Issue issue = new Issue(user, location, "road", "Large pothole causing traffic issues", LocalDateTime.now(),
				List.of(media), 42, 10, "OPEN", 1);

		// ---- Viewer Context ----
		ViewerContext viewerContext = new ViewerContext(true);

		// ---- Data ----
		ResponseData data = new ResponseData(issue, viewerContext);

		return new APIResponse(data);
	}
}
