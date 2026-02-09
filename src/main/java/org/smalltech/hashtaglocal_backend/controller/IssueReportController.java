package org.smalltech.hashtaglocal_backend.controller;

import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import java.time.LocalDateTime;
import org.smalltech.hashtaglocal_backend.entity.IssueEntity;
import org.smalltech.hashtaglocal_backend.entity.Locality;
import org.smalltech.hashtaglocal_backend.entity.Location;
import org.smalltech.hashtaglocal_backend.entity.MediaEntity;
import org.smalltech.hashtaglocal_backend.entity.UserEntity;
import org.smalltech.hashtaglocal_backend.model.APIResponse;
import org.smalltech.hashtaglocal_backend.model.IssueStatusModel;
import org.smalltech.hashtaglocal_backend.model.IssueTypeModel;
import org.smalltech.hashtaglocal_backend.model.MediaTypeModel;
import org.smalltech.hashtaglocal_backend.model.ResponseData;
import org.smalltech.hashtaglocal_backend.model.request.IssueReportRequest;
import org.smalltech.hashtaglocal_backend.model.request.MediaRequest;
import org.smalltech.hashtaglocal_backend.repository.IssueRepository;
import org.smalltech.hashtaglocal_backend.repository.LocalityRepository;
import org.smalltech.hashtaglocal_backend.repository.LocationRepository;
import org.smalltech.hashtaglocal_backend.repository.MediaRepository;
import org.smalltech.hashtaglocal_backend.repository.UserRepository;
import org.smalltech.hashtaglocal_backend.util.LocationUtil;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/issue")
@SecurityRequirement(name = "bearerAuth")
public class IssueReportController {

	private final IssueRepository issueRepository;
	private final LocationRepository locationRepository;
	private final MediaRepository mediaRepository;
	private final LocalityRepository localityRepository;
	private final UserRepository userRepository;
	public IssueReportController(IssueRepository issueRepository, LocationRepository locationRepository,
			MediaRepository mediaRepository, LocalityRepository localityRepository, UserRepository userRepository) {
		this.issueRepository = issueRepository;
		this.locationRepository = locationRepository;
		this.mediaRepository = mediaRepository;
		this.localityRepository = localityRepository;
		this.userRepository = userRepository;
	}

	@PostMapping
	@Transactional
	public ResponseEntity<APIResponse> createIssue(@AuthenticationPrincipal Long userId,
			@RequestBody IssueReportRequest request) {

		var issueReq = request.getIssue();
		// Get default #world locality (ID 1)
		var defaultLocality = localityRepository.findById(1L).orElse(null);
		var issueLocality = resolveLocality(issueReq.getLocation().getLat(), issueReq.getLocation().getLng(),
				defaultLocality);

		UserEntity user = userRepository.findById(userId).orElseThrow(() -> new RuntimeException("User not found"));

		// Save issue location
		Location issueLocation = Location.builder()
				.point(LocationUtil.createPoint(issueReq.getLocation().getLat(), issueReq.getLocation().getLng()))
				.name(issueLocality != null ? issueLocality.getName() : "Unknown").locality(issueLocality)
				.metaData(issueReq.getLocation().getMetaData()).build();

		issueLocation = locationRepository.save(issueLocation);

		// Create issue with user assigned
		IssueEntity issue = IssueEntity.builder().type(IssueTypeModel.valueOf(issueReq.getType()))
				.description(issueReq.getDescription()).status(IssueStatusModel.ONHOLD).createdAt(LocalDateTime.now())
				.updatedAt(LocalDateTime.now()).location(issueLocation).userEntity(user).build();

		issue = issueRepository.save(issue);

		// Save Media (if provided)
		if (issueReq.getMediaUrls() != null && !issueReq.getMediaUrls().isEmpty()) {
			for (MediaRequest mediaReq : issueReq.getMediaUrls()) {
				var mediaLocality = resolveLocality(mediaReq.getLocation().getLat(), mediaReq.getLocation().getLng(),
						defaultLocality);

				// Save Media Location
				Location mediaLocation = Location.builder()
						.point(LocationUtil.createPoint(mediaReq.getLocation().getLat(),
								mediaReq.getLocation().getLng()))
						.name(mediaLocality != null ? mediaLocality.getName() : "Unknown").locality(mediaLocality)
						.metaData(mediaReq.getLocation().getMetaData()).build();

				mediaLocation = locationRepository.save(mediaLocation);

				// Save Media
				MediaEntity media = MediaEntity.builder().issue(issue).type(MediaTypeModel.valueOf(mediaReq.getType()))
						.url(mediaReq.getUrl()).user(user).location(mediaLocation).createdAt(LocalDateTime.now())
						.build();

				mediaRepository.save(media);
			}
		}

		ResponseData responseData = ResponseData.builder().issueId(issue.getId()).build();

		APIResponse response = APIResponse.builder().data(responseData).build();

		return ResponseEntity.ok(response);
	}

	private Locality resolveLocality(Double latitude, Double longitude, Locality defaultLocality) {
		if (latitude == null || longitude == null) {
			return defaultLocality;
		}
		return localityRepository.findContainingLocality(latitude, longitude)
				.or(() -> localityRepository.findNearestLocality(latitude, longitude)).orElse(defaultLocality);
	}
}
