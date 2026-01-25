package org.smalltech.hashtaglocal_backend.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.Point;
import org.locationtech.jts.geom.Polygon;
import org.mockito.Mockito;
import org.smalltech.hashtaglocal_backend.entity.IssueEntity;
import org.smalltech.hashtaglocal_backend.entity.Locality;
import org.smalltech.hashtaglocal_backend.entity.Location;
import org.smalltech.hashtaglocal_backend.entity.MediaEntity;
import org.smalltech.hashtaglocal_backend.entity.UserEntity;
import org.smalltech.hashtaglocal_backend.model.APIResponse;
import org.smalltech.hashtaglocal_backend.model.IssueStatusModel;
import org.smalltech.hashtaglocal_backend.model.IssueTypeModel;
import org.smalltech.hashtaglocal_backend.model.MediaTypeModel;
import org.smalltech.hashtaglocal_backend.repository.IssueRepository;
import org.smalltech.hashtaglocal_backend.repository.MediaRepository;
import org.smalltech.hashtaglocal_backend.service.GCSService;

class IssueHomeControllerTests {

	private IssueRepository issueRepository;
	private MediaRepository mediaRepository;
	private GCSService gcsService;
	private IssueHomeController controller;

	@BeforeEach
	void setup() {
		issueRepository = Mockito.mock(IssueRepository.class);
		mediaRepository = Mockito.mock(MediaRepository.class);
		gcsService = Mockito.mock(GCSService.class);
		controller = new IssueHomeController(issueRepository, mediaRepository, gcsService);
	}

	@Test
	void getIssues_shouldReturnValidApiResponse() {
		// Arrange
		GeometryFactory geometryFactory = new GeometryFactory();

		// Create test user
		UserEntity user = UserEntity.builder().id(1L).username("john_doe")
				.profilePicture("https://example.com/profile.jpg").locale("en_US").build();

		// Create test locality
		Polygon polygon = geometryFactory
				.createPolygon(new Coordinate[]{new Coordinate(75.7, 26.8), new Coordinate(75.9, 26.8),
						new Coordinate(75.9, 27.0), new Coordinate(75.7, 27.0), new Coordinate(75.7, 26.8)});

		Locality locality = Locality.builder().id(1L).hashtag("Jaipur").name("Jaipur").geoBoundary(polygon).build();

		// Create test location
		Point point = geometryFactory.createPoint(new Coordinate(56.78, 12.34));
		Map<String, Object> metaData = new HashMap<>();
		metaData.put("address", "Sector 3, Jawahar Nagar");
		metaData.put("colloquialName", "Near Patrika Gate");

		Location location = Location.builder().id(1L).point(point).locality(locality).name("Sector 3, Jawahar Nagar")
				.metaData(metaData).build();

		// Create issue 1 - older
		IssueEntity issue1 = IssueEntity.builder().id(1L).key("JPR-001").type(IssueTypeModel.POTHOLE)
				.status(IssueStatusModel.OPEN).description("Large pothole causing traffic issues")
				.createdAt(LocalDateTime.parse("2025-12-25T10:00:00"))
				.updatedAt(LocalDateTime.parse("2025-12-25T10:00:00")).userEntity(user).location(location).build();

		// Create issue 2 - newer
		IssueEntity issue2 = IssueEntity.builder().id(2L).key("JPR-002").type(IssueTypeModel.POTHOLE)
				.status(IssueStatusModel.OPEN).description("Large pothole causing traffic issues")
				.createdAt(LocalDateTime.parse("2025-12-26T18:00:00"))
				.updatedAt(LocalDateTime.parse("2025-12-26T18:00:00")).userEntity(user).location(location).build();

		// Create media for both issues
		MediaEntity media1 = MediaEntity.builder().id(1L).issue(issue1).type(MediaTypeModel.PHOTO)
				.url("https://sripath.com/wp-content/uploads/2025/01/iStock-174662203.jpg").location(location).build();

		MediaEntity media2 = MediaEntity.builder().id(2L).issue(issue1).type(MediaTypeModel.PHOTO)
				.url("https://nub.news/api/image/526263/article.png").location(location).build();

		MediaEntity media3 = MediaEntity.builder().id(3L).issue(issue2).type(MediaTypeModel.PHOTO)
				.url("https://sripath.com/wp-content/uploads/2025/01/iStock-174662203.jpg").location(location).build();

		MediaEntity media4 = MediaEntity.builder().id(4L).issue(issue2).type(MediaTypeModel.PHOTO)
				.url("https://nub.news/api/image/526263/article.png").location(location).build();

		when(issueRepository
				.findByStatusInOrderByCreatedAtDesc(List.of(IssueStatusModel.OPEN, IssueStatusModel.ONHOLD)))
				.thenReturn(List.of(issue2, issue1));
		when(mediaRepository.findByIssue(issue1)).thenReturn(List.of(media1, media2));
		when(mediaRepository.findByIssue(issue2)).thenReturn(List.of(media3, media4));

		// Act
		APIResponse actualResponse = controller.getIssues();

		// Assert
		assertNotNull(actualResponse);
		assertNotNull(actualResponse.getData());
		assertNotNull(actualResponse.getData().getIssues());
		assertEquals(2, actualResponse.getData().getIssues().size());

		// Verify first issue is the newer one (issue 2)
		var firstIssue = actualResponse.getData().getIssues().get(0);
		assertEquals(2L, firstIssue.getId());
		assertEquals("john_doe", firstIssue.getUser().getUsername());
		assertEquals("pothole", firstIssue.getType());
		assertEquals("Large pothole causing traffic issues", firstIssue.getDescription());
		assertEquals(2, firstIssue.getMediaUrls().size());

		// Verify second issue is the older one (issue 1)
		var secondIssue = actualResponse.getData().getIssues().get(1);
		assertEquals(1L, secondIssue.getId());
		assertEquals("john_doe", secondIssue.getUser().getUsername());
		assertEquals(2, secondIssue.getMediaUrls().size());
	}
}
