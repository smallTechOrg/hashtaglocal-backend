package org.smalltech.hashtaglocal_backend.integration;

import java.io.File;
import java.nio.file.Files;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.smalltech.hashtaglocal_backend.entity.IssueEntity;
import org.smalltech.hashtaglocal_backend.entity.Location;
import org.smalltech.hashtaglocal_backend.entity.MediaEntity;
import org.smalltech.hashtaglocal_backend.repository.IssueRepository;
import org.smalltech.hashtaglocal_backend.repository.LocationRepository;
import org.smalltech.hashtaglocal_backend.repository.MediaRepository;
import org.smalltech.hashtaglocal_backend.util.LocationUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.annotation.Rollback;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.ResourceUtils;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class IssueReportIntegrationTests {

	@Autowired
	private WebTestClient webTestClient;
	@Autowired
	private IssueRepository issueRepository;
	@Autowired
	private LocationRepository locationRepository;
	@Autowired
	private MediaRepository mediaRepository;

	@Test
	@Transactional
	@Rollback
	void createIssue_shouldReturnSuccessResponse() throws Exception {
		// Load request JSON from test resources
		File jsonFile = ResourceUtils.getFile("classpath:issue-report-request.json");
		String requestJson = Files.readString(jsonFile.toPath());

		webTestClient.post().uri("/api/v1/issue").contentType(MediaType.APPLICATION_JSON).bodyValue(requestJson)
				.exchange().expectStatus().isOk().expectBody().jsonPath("$.data.issue_id").exists();

		// Assert DB entries
		List<IssueEntity> issues = issueRepository.findAll();
		assert !issues.isEmpty();
		IssueEntity issue = issues.get(issues.size() - 1);
		assert issue.getDescription().equals("Test pothole issue integration");
		assert issue.getType().name().equals("POTHOLE");
		assert issue.getStatus().name().equals("OPEN");
		assert issue.getLocation() != null;
		Location issueLocation = locationRepository.findById(issue.getLocation().getId()).orElseThrow();
		assert LocationUtil.getLatitude(issueLocation.getPoint()).equals(28.7041);
		assert LocationUtil.getLongitude(issueLocation.getPoint()).equals(77.1025);
		assert issueLocation.getMetaData() != null && issueLocation.getMetaData().toString().contains("Delhi");

		List<MediaEntity> mediaList = mediaRepository.findAll();
		assert !mediaList.isEmpty();
		MediaEntity media = mediaList.get(mediaList.size() - 1);
		assert media.getType().name().equals("PHOTO");
		assert media.getUrl().equals("https://example.com/test-photo.jpg");
		assert media.getLocation() != null;
		Location mediaLocation = locationRepository.findById(media.getLocation().getId()).orElseThrow();
		assert LocationUtil.getLatitude(mediaLocation.getPoint()).equals(28.7041);
		assert LocationUtil.getLongitude(mediaLocation.getPoint()).equals(77.1025);
		assert mediaLocation.getMetaData() != null && mediaLocation.getMetaData().toString().contains("Delhi");
	}
}
