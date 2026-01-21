package org.smalltech.hashtaglocal_backend.controller;

import java.time.LocalDateTime;
import org.smalltech.hashtaglocal_backend.entity.IssueEntity;
import org.smalltech.hashtaglocal_backend.entity.Location;
import org.smalltech.hashtaglocal_backend.entity.MediaEntity;
import org.smalltech.hashtaglocal_backend.model.IssueStatusModel;
import org.smalltech.hashtaglocal_backend.model.IssueTypeModel;
import org.smalltech.hashtaglocal_backend.model.MediaTypeModel;
import org.smalltech.hashtaglocal_backend.model.request.IssueReportRequest;
import org.smalltech.hashtaglocal_backend.repository.IssueRepository;
import org.smalltech.hashtaglocal_backend.repository.LocationRepository;
import org.smalltech.hashtaglocal_backend.repository.MediaRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/issue")
public class IssueWriteController {

	private final IssueRepository issueRepository;
	private final LocationRepository locationRepository;
	private final MediaRepository mediaRepository;

	public IssueWriteController(IssueRepository issueRepository, LocationRepository locationRepository,
			MediaRepository mediaRepository) {
		this.issueRepository = issueRepository;
		this.locationRepository = locationRepository;
		this.mediaRepository = mediaRepository;
	}

	@PostMapping
	public ResponseEntity<Void> createIssue(@RequestBody IssueReportRequest request) {

		var issueReq = request.getIssue();

		/* 1️⃣ Save issue location */
		Location issueLocation = Location.builder().lat(issueReq.getLocation().getLat())
				.lng(issueReq.getLocation().getLng()).metaData(issueReq.getLocation().getMetaData()).build();

		issueLocation = locationRepository.save(issueLocation);

		/* 2️⃣ Create issue */
		IssueEntity issue = IssueEntity.builder().type(IssueTypeModel.valueOf(issueReq.getType()))
				.description(issueReq.getDescription()).status(IssueStatusModel.OPEN).createdAt(LocalDateTime.now())
				.updatedAt(LocalDateTime.now()).location(issueLocation).build();

		issue = issueRepository.save(issue);

		// Save media
		if (issueReq.getMediaUrls() != null) {
			for (var mediaReq : issueReq.getMediaUrls()) {

				Location mediaLocation = null;

				if (mediaReq.getLocation() != null) {
					mediaLocation = Location.builder().lat(mediaReq.getLocation().getLat())
							.lng(mediaReq.getLocation().getLng()).metaData(mediaReq.getLocation().getMetaData())
							.build();

					mediaLocation = locationRepository.save(mediaLocation);
				}

				MediaEntity media = MediaEntity.builder().issue(issue).type(MediaTypeModel.valueOf(mediaReq.getType()))
						.url(mediaReq.getUrl()).location(mediaLocation).build();

				mediaRepository.save(media);
			}
		}

		return ResponseEntity.ok().build();
	}
}
