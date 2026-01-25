package org.smalltech.hashtaglocal_backend.integration;

import static org.junit.jupiter.api.Assertions.*;

import jakarta.persistence.EntityManager;
import java.io.File;
import java.nio.file.Files;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.Polygon;
import org.smalltech.hashtaglocal_backend.entity.IssueEntity;
import org.smalltech.hashtaglocal_backend.entity.Location;
import org.smalltech.hashtaglocal_backend.entity.MediaEntity;
import org.smalltech.hashtaglocal_backend.repository.IssueRepository;
import org.smalltech.hashtaglocal_backend.repository.LocalityRepository;
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
	@Autowired
	private LocalityRepository localityRepository;
	@Autowired
	private EntityManager entityManager;

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
		assert issue.getStatus().name().equals("ONHOLD");
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

	@Test
	void createIssue_shouldAssignBengaluruLocality() {
		// Seed Bengaluru locality polygon (SRID 4326, lon/lat order)
		GeometryFactory gf = new GeometryFactory();
		Coordinate[] coords = new Coordinate[]{new Coordinate(77.3791981, 12.7342888),
				new Coordinate(78.037506, 12.7342888), new Coordinate(78.037506, 13.173706),
				new Coordinate(77.3791981, 13.173706), new Coordinate(77.3791981, 12.7342888)};
		Polygon polygon = gf.createPolygon(coords);
		polygon.setSRID(4326);
		var bengaluru = org.smalltech.hashtaglocal_backend.entity.Locality.builder().hashtag("#bengaluru")
				.name("Bengaluru").geoBoundary(polygon).build();
		localityRepository.saveAndFlush(bengaluru);

		String requestJson = """
				{
				  "issue": {
				    "type": "POTHOLE",
				    "description": "Pothole in Bengaluru",
				    "location": {
				      "lat": 12.9629,
				      "lng": 77.5775,
				      "meta_data": {"city": "Bengaluru"}
				    },
				    "media_urls": []
				  }
				}
				""";

		webTestClient.post().uri("/api/v1/issue").contentType(MediaType.APPLICATION_JSON).bodyValue(requestJson)
				.exchange().expectStatus().isOk().expectBody().jsonPath("$.data.issue_id").exists();

		List<IssueEntity> issues = issueRepository.findAll();
		IssueEntity latest = issues.stream().max((a, b) -> Long.compare(a.getId(), b.getId())).orElseThrow();
		Long locId = latest.getLocation().getId();
		Long localityId = ((Number) entityManager.createNativeQuery("select locality_id from locations where id = :id")
				.setParameter("id", locId).getSingleResult()).longValue();
		var locality = localityRepository.findById(localityId).orElse(null);
		assertNotNull(locality, "Locality should be assigned");
		assertEquals("#bengaluru", locality.getHashtag(), "Hashtag assigned: " + locality.getHashtag());
	}
}
