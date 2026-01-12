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
import org.smalltech.hashtaglocal_backend.repository.IssueRepository;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/issue")
@Tag(name = "Issue", description = "issue API")

public class IssueController {

	private final IssueRepository issueRepository;

	public IssueController(IssueRepository issueRepository) {
		this.issueRepository = issueRepository;
	}

	@GetMapping("/{issueId}")
	@Operation(summary = "Get issue", description = "Returns a issue response with user, location, locality and viewer context.")
	@ApiResponse(responseCode = "200", description = "Successful issue response", content = @Content(mediaType = "application/json", schema = @Schema(implementation = APIResponse.class)))
	public APIResponse getIssue(@PathVariable Long issueId) {
		// Try fetching the requested issue
		var issueEntity = issueRepository.findById(issueId)
				// If not found, fetch issue with ID 1 as fallback
				.orElseGet(() -> issueRepository.findById(1L)
						.orElseThrow(() -> new RuntimeException("No issue available")));
		return mapToAPIResponse(issueEntity);
	}

	private APIResponse mapToAPIResponse(org.smalltech.hashtaglocal_backend.entity.IssueEntity entity) {

		User user = new User("john_doe", "https://example.com/profile.jpg");
		Locality locality = new Locality(List.of("#Jaipur"));
		Location location = new Location("12.34", "56.78", locality, "Sector 3, Jawahar Nagar", "Near Patrika Gate");
		Media media1 = new Media(location, "photo",
				"https://sripath.com/wp-content/uploads/2025/01/iStock-174662203.jpg");
		Media media2 = new Media(location, "photo", "https://nub.news/api/image/526263/article.png");
		ViewerContext viewerContext = new ViewerContext(true);
		Issue issue = new Issue(user, location, entity.getType(), entity.getDescription(), entity.getCreatedAt(),
				List.of(media1, media2), 42, 10, entity.getStatus(), 1, viewerContext);
		ResponseData data = new ResponseData(issue);

		return new APIResponse(data);
	}
}
