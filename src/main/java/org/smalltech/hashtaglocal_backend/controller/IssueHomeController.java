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
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/issues")
@Tag(name = "Issues Home", description = "issue home API")
@Transactional(readOnly = true)
public class IssueHomeController {

	private final IssueRepository issueRepository;
	private final MediaRepository mediaRepository;

	public IssueHomeController(IssueRepository issueRepository, MediaRepository mediaRepository) {
		this.issueRepository = issueRepository;
		this.mediaRepository = mediaRepository;
	}

	@GetMapping
	@Operation(summary = "Get issue Home", description = "Returns a List of issues with user, location, locality and viewer context.")
	@ApiResponse(responseCode = "200", description = "Successful issue response", content = @Content(mediaType = "application/json", schema = @Schema(implementation = APIResponse.class)))
	public APIResponse getIssues() {
		// Fetch all issues from database
		List<org.smalltech.hashtaglocal_backend.entity.IssueEntity> issueEntities = issueRepository.findAll();

		List<Issue> issues = issueEntities.stream().map(this::mapToIssue).toList();

		ResponseData data = ResponseData.builder().issues(issues).build();
		return APIResponse.builder().data(data).build();
	}

	private Issue mapToIssue(org.smalltech.hashtaglocal_backend.entity.IssueEntity entity) {
		// Get user from entity - IssueDataInitializer ensures all issues have user ID 1
		org.smalltech.hashtaglocal_backend.entity.UserEntity userEntity = entity.getUserEntity();
		if (userEntity == null) {
			// Fallback: create a default user if data is corrupted
			userEntity = new org.smalltech.hashtaglocal_backend.entity.UserEntity();
			userEntity.setUsername("admin");
			userEntity.setProfilePicture("https://example.com/default-profile.jpg");
		}
		User user = User.builder().username(userEntity.getUsername()).profilePhoto(userEntity.getProfilePicture())
				.build();

		// Map Locality from Location entity with robust null-safety
		org.smalltech.hashtaglocal_backend.entity.Location locEntity = entity.getLocation();
		String hashtag = "world";
		if (locEntity != null && locEntity.getLocality() != null && locEntity.getLocality().getHashtag() != null) {
			hashtag = locEntity.getLocality().getHashtag();
		}
		Locality locality = Locality.builder().hashtags(List.of("#" + hashtag)).build();

		double lat = 0.0;
		double lng = 0.0;
		String name = "Unknown";
		if (locEntity != null) {
			if (locEntity.getPoint() != null) {
				lat = locEntity.getPoint().getY();
				lng = locEntity.getPoint().getX();
			}
			if (locEntity.getName() != null) {
				name = locEntity.getName();
			}
		}
		Location location = Location.builder().lat(lat).lng(lng).locality(locality).address(name).colloquialName(name)
				.build();

		// Fetch media items from database
		List<org.smalltech.hashtaglocal_backend.entity.MediaEntity> mediaEntities = mediaRepository.findByIssue(entity);
		List<Media> mediaList = mediaEntities.stream().map(mediaEntity -> Media.builder().location(location)
				.type(mediaEntity.getType().name().toLowerCase()).url(mediaEntity.getUrl()).build()).toList();

		// Default viewer context (no upvote data in DB yet)
		ViewerContext viewerContext = ViewerContext.builder().upvote(false).build();

		return Issue.builder().id(entity.getId()).user(user).location(location)
				.type(entity.getType().name().toLowerCase()).description(entity.getDescription())
				.createdAt(entity.getCreatedAt()).mediaUrls(mediaList).voteCount(0).verifyCount(0)
				.status(entity.getStatus().name()).rank(1).viewerContext(viewerContext).build();
	}

}
