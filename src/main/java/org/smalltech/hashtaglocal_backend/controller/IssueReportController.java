package org.smalltech.hashtaglocal_backend.controller;

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
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/issue")
public class IssueReportController {

	private final IssueRepository issueRepository;
	private final LocationRepository locationRepository;
	private final MediaRepository mediaRepository;
	private final UserRepository userRepository;
	private final LocalityRepository localityRepository;

	public IssueReportController(IssueRepository issueRepository, LocationRepository locationRepository,
			MediaRepository mediaRepository, UserRepository userRepository, LocalityRepository localityRepository) {
		this.issueRepository = issueRepository;
		this.locationRepository = locationRepository;
		this.mediaRepository = mediaRepository;
		this.userRepository = userRepository;
		this.localityRepository = localityRepository;
	}

	@PostMapping
	@Transactional
	public ResponseEntity<APIResponse> createIssue(@RequestBody IssueReportRequest request) {

		var issueReq = request.getIssue();
		// Get default #world locality (ID 1)
		var defaultLocality = localityRepository.findById(1L).orElse(null);
		var issueLocality = resolveLocality(issueReq.getLocation().getLat(), issueReq.getLocation().getLng(),
				defaultLocality);

		// Get default admin user (User 1) or first user
		UserEntity user = userRepository.findById(1L).orElseGet(() -> {
			var allUsers = userRepository.findAll();
			return allUsers.isEmpty() ? null : allUsers.get(0);
		});

		// Save issue location
		Location issueLocation = Location.builder()
				.point(LocationUtil.createPoint(issueReq.getLocation().getLat(), issueReq.getLocation().getLng()))
				.name(issueLocality != null ? issueLocality.getName() : "Unknown").locality(issueLocality)
				.metaData(issueReq.getLocation().getMetaData()).build();

		issueLocation = locationRepository.save(issueLocation);

		// Create issue with user assigned
		IssueEntity issue = IssueEntity.builder().type(IssueTypeModel.valueOf(issueReq.getType()))
				.description(issueReq.getDescription()).status(IssueStatusModel.OPEN).createdAt(LocalDateTime.now())
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
						.url(mediaReq.getUrl()).location(mediaLocation).build();

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
