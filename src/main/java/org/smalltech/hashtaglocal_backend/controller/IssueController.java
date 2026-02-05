package org.smalltech.hashtaglocal_backend.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import org.smalltech.hashtaglocal_backend.dto.LocationMetadataDTO;
import org.smalltech.hashtaglocal_backend.model.APIResponse;
import org.smalltech.hashtaglocal_backend.model.Issue;
import org.smalltech.hashtaglocal_backend.model.IssueStatusModel;
import org.smalltech.hashtaglocal_backend.model.IssueTypeModel;
import org.smalltech.hashtaglocal_backend.model.Locality;
import org.smalltech.hashtaglocal_backend.model.Location;
import org.smalltech.hashtaglocal_backend.model.Media;
import org.smalltech.hashtaglocal_backend.model.ResponseData;
import org.smalltech.hashtaglocal_backend.model.User;
import org.smalltech.hashtaglocal_backend.model.ViewerContext;
import org.smalltech.hashtaglocal_backend.model.request.IssuePatchRequest;
import org.smalltech.hashtaglocal_backend.model.request.IssueVerifyRequest;
import org.smalltech.hashtaglocal_backend.repository.IssueRepository;
import org.smalltech.hashtaglocal_backend.repository.MediaRepository;
import org.smalltech.hashtaglocal_backend.repository.UserRepository;
import org.smalltech.hashtaglocal_backend.service.GCSService;
import org.smalltech.hashtaglocal_backend.service.GoogleMapsGeocodingService;
import org.smalltech.hashtaglocal_backend.util.LocationUtil;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/v1/issue")
@Tag(name = "Issue", description = "issue API")
@Transactional(readOnly = true)
public class IssueController {

	private final IssueRepository issueRepository;
	private final MediaRepository mediaRepository;
	private final UserRepository userRepository;
	private final GCSService gcsService;
	private final GoogleMapsGeocodingService googleMapsGeocodingService;

	public IssueController(IssueRepository issueRepository, MediaRepository mediaRepository,
			UserRepository userRepository, GCSService gcsService,
			GoogleMapsGeocodingService googleMapsGeocodingService) {
		this.issueRepository = issueRepository;
		this.mediaRepository = mediaRepository;
		this.userRepository = userRepository;
		this.gcsService = gcsService;
		this.googleMapsGeocodingService = googleMapsGeocodingService;
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

	@PatchMapping("/{issueId}")
	@Transactional
	@Operation(summary = "Update issue", description = "Patch issue fields like status, type, description, and coordinates.")
	@ApiResponse(responseCode = "200", description = "Issue patched successfully", content = @Content(mediaType = "application/json", schema = @Schema(implementation = APIResponse.class)))
	public ResponseEntity<APIResponse> patchIssue(@PathVariable Long issueId, @RequestBody IssuePatchRequest request) {
		if (request == null) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Request body is required");
		}

		var issueEntity = issueRepository.findById(issueId)
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Issue not found"));

		boolean updated = false;

		if (request.getStatus() != null) {
			issueEntity.setStatus(parseStatus(request.getStatus()));
			updated = true;
		}

		if (request.getType() != null) {
			issueEntity.setType(parseType(request.getType()));
			updated = true;
		}

		if (request.getDescription() != null) {
			issueEntity.setDescription(request.getDescription());
			updated = true;
		}

		if (request.getLat() != null && request.getLng() != null) {
			updateLocationCoordinates(issueEntity, request.getLat(), request.getLng());
			updated = true;
		}

		if (updated) {
			issueEntity.setUpdatedAt(LocalDateTime.now());
			issueRepository.save(issueEntity);
		}

		return ResponseEntity.ok(mapToAPIResponse(issueEntity));
	}

	@PutMapping("/{issueId}")
	@SecurityRequirement(name = "bearerAuth")
	@Transactional
	@Operation(summary = "Verify or resolve issue", description = "Verify an issue with media attachments and create verification records.")
	@ApiResponse(responseCode = "200", description = "Issue verified successfully", content = @Content(mediaType = "application/json", schema = @Schema(implementation = APIResponse.class)))
	public ResponseEntity<APIResponse> verifyIssue(@PathVariable Long issueId, @AuthenticationPrincipal Long userId,
			@RequestBody IssueVerifyRequest request) {

		// 1. Debug the Raw Request Path
		System.out.println("DEBUG: Received verify request for issueId: " + issueId);
		System.out.println("DEBUG: Authenticated userId: " + userId);

		if (request == null || request.getIssueAction() == null) {
			System.out.println("DEBUG: ERROR - Request body is null");
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Request body with issueAction is required");
		}

		String action = request.getIssueAction().getAction();
		if (action == null || !(action.equalsIgnoreCase("VERIFY") || action.equalsIgnoreCase("RESOLVED"))) {
			System.out.println("DEBUG: ERROR - Invalid action: " + action);
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid action. Expected VERIFY or RESOLVED");
		}

		var issueEntity = issueRepository.findById(issueId)
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Issue not found"));

		// Resolve authenticated user from security context
		if (userId == null) {
			throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "User not authenticated");
		}
		var userEntity = userRepository.findById(userId)
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "User not found"));

		// Process media URLs if provided
		if (request.getIssueAction().getMediaUrls() != null && !request.getIssueAction().getMediaUrls().isEmpty()) {
			for (var mediaRequest : request.getIssueAction().getMediaUrls()) {

				System.out.println("DEBUG media type: " + mediaRequest.getType());
				System.out.println("DEBUG media url: " + mediaRequest.getUrl());
				System.out.println("DEBUG media desc: " + mediaRequest.getDescription());

				var mediaEntity = org.smalltech.hashtaglocal_backend.entity.MediaEntity.builder().issue(issueEntity)
						.type(parseMediaType(mediaRequest.getType())).url(mediaRequest.getUrl()).user(userEntity)
						.description(mediaRequest.getDescription()).createdAt(LocalDateTime.now()).build();
				mediaRepository.save(mediaEntity);
			}
		}

		// Action-based status update
		if (action.equalsIgnoreCase("VERIFY")) {
			issueEntity.setStatus(IssueStatusModel.OPEN);
		} else if (action.equalsIgnoreCase("RESOLVED")) {
			issueEntity.setStatus(IssueStatusModel.PENDING);
		}
		issueEntity.setUpdatedAt(LocalDateTime.now());
		issueRepository.save(issueEntity);

		// Build response
		APIResponse response = APIResponse.builder().data(ResponseData.builder().issueId(issueId).build()).build();

		return ResponseEntity.ok(response);
	}

	private org.smalltech.hashtaglocal_backend.model.MediaTypeModel parseMediaType(String type) {
		try {
			return org.smalltech.hashtaglocal_backend.model.MediaTypeModel.valueOf(type.toUpperCase(Locale.ROOT));
		} catch (IllegalArgumentException ex) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid media type: " + type, ex);
		}
	}

	private IssueStatusModel parseStatus(String status) {
		try {
			return IssueStatusModel.valueOf(status.toUpperCase(Locale.ROOT));
		} catch (IllegalArgumentException ex) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid status value: " + status, ex);
		}
	}

	private IssueTypeModel parseType(String type) {
		try {
			return IssueTypeModel.valueOf(type.toUpperCase(Locale.ROOT));
		} catch (IllegalArgumentException ex) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid type value: " + type, ex);
		}
	}

	private void updateLocationCoordinates(org.smalltech.hashtaglocal_backend.entity.IssueEntity issueEntity,
			Double lat, Double lng) {
		org.smalltech.hashtaglocal_backend.entity.Location locEntity = issueEntity.getLocation();
		if (locEntity != null) {
			// Update point with new coordinates
			locEntity.setPoint(LocationUtil.createPoint(lat, lng));

			// Fetch latest location metadata from Google Maps
			LocationMetadataDTO metadata = googleMapsGeocodingService.reverseGeocode(lat, lng);
			if (metadata != null) {
				// Update location metadata
				java.util.Map<String, Object> metadataMap = googleMapsGeocodingService.metadataToMap(metadata);
				locEntity.setMetaData(metadataMap);

				// Update location name if we have a good one
				if (metadata.getName() != null && !metadata.getName().isEmpty()) {
					locEntity.setName(metadata.getName());
				} else if (metadata.getCity() != null) {
					locEntity.setName(metadata.getCity());
				}
			}
		} else {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Location data not found for issue");
		}
	}

	private APIResponse mapToAPIResponse(org.smalltech.hashtaglocal_backend.entity.IssueEntity entity) {
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
		Locality locality = Locality.builder().hashtags(List.of(hashtag)).build();

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
		List<Media> mediaList = mediaEntities.stream().map(mediaEntity -> {
			String username = "admin";
			if (mediaEntity.getUser() != null && mediaEntity.getUser().getUsername() != null) {
				username = mediaEntity.getUser().getUsername();
			}
			return Media.builder().location(location).type(mediaEntity.getType().name().toLowerCase())
					.url(gcsService.generateSignedUrl(mediaEntity.getUrl())).description(mediaEntity.getDescription())
					.username(username).createdAt(mediaEntity.getCreatedAt()).build();
		}).toList();

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
