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
import org.smalltech.hashtaglocal_backend.repository.MediaRepository;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/issue")
@Tag(name = "Issue", description = "issue API")
@Transactional(readOnly = true)
public class IssueController {

	private final IssueRepository issueRepository;
	private final MediaRepository mediaRepository;

	public IssueController(IssueRepository issueRepository, MediaRepository mediaRepository) {
		this.issueRepository = issueRepository;
		this.mediaRepository = mediaRepository;
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
		// Map User from UserEntity
		User user = User.builder().username(entity.getUserEntity().getUsername())
				.profilePhoto(entity.getUserEntity().getProfilePicture()).build();

		// Map Locality from Location entity
		Locality locality = Locality.builder().hashtags(List.of("#" + entity.getLocation().getLocality().getHashtag()))
				.build();

		// Extract coordinates from JTS Point and map Location
		Location location = Location.builder().lat(entity.getLocation().getPoint().getY())
				.lng(entity.getLocation().getPoint().getX()).locality(locality).address(entity.getLocation().getName())
				.colloquialName(entity.getLocation().getName()).build();

		// Fetch media items from database
		List<org.smalltech.hashtaglocal_backend.entity.MediaEntity> mediaEntities = mediaRepository.findByIssue(entity);
		List<Media> mediaList = mediaEntities.stream().map(mediaEntity -> Media.builder().location(location)
				.type(mediaEntity.getType().name().toLowerCase()).url(mediaEntity.getUrl()).build()).toList();

		// Default viewer context (no upvote data in DB yet)
		ViewerContext viewerContext = ViewerContext.builder().upvote(false).build();

		Issue issue = Issue.builder().id(entity.getId()).user(user).location(location)
				.type(entity.getType().name().toLowerCase()).description(entity.getDescription())
				.createdAt(entity.getCreatedAt()).mediaUrls(mediaList).voteCount(0).verifyCount(0)
				.status(entity.getStatus().name()).rank(1).viewerContext(viewerContext).build();

		ResponseData data = ResponseData.builder().issue(issue).build();

		return APIResponse.builder().data(data).build();
	}

}
