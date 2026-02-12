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

class IssueHomeV2ControllerTests {

	private IssueRepository issueRepository;
	private MediaRepository mediaRepository;
	private GCSService gcsService;
	private IssueHomeV2Controller controller;

	@BeforeEach
	void setup() {
		issueRepository = Mockito.mock(IssueRepository.class);
		mediaRepository = Mockito.mock(MediaRepository.class);
		gcsService = Mockito.mock(GCSService.class);
		controller = new IssueHomeV2Controller(issueRepository, mediaRepository, gcsService);
	}

	@Test
	void getIssuesNearby_shouldReturnIssuesWithin5KmRadius() {
		// Arrange
		GeometryFactory geometryFactory = new GeometryFactory();
		UserEntity user = UserEntity.builder().id(1L).username("john_doe")
				.profilePicture("https://example.com/profile.jpg").locale("en_US").build();
		Polygon polygon = geometryFactory
				.createPolygon(new Coordinate[]{new Coordinate(75.7, 26.8), new Coordinate(75.9, 26.8),
						new Coordinate(75.9, 27.0), new Coordinate(75.7, 27.0), new Coordinate(75.7, 26.8)});
		Locality locality = Locality.builder().id(1L).hashtag("Jaipur").name("Jaipur").geoBoundary(polygon).build();
		Point point = geometryFactory.createPoint(new Coordinate(75.8073, 26.9124)); // lng, lat
		Map<String, Object> metaData = new HashMap<>();
		metaData.put("address", "Sector 3, Jawahar Nagar");
		metaData.put("colloquialName", "Near Patrika Gate");
		Location location = Location.builder().id(1L).point(point).locality(locality).name("Sector 3, Jawahar Nagar")
				.metaData(metaData).build();
		IssueEntity issue1 = IssueEntity.builder().id(1L).key("JPR-001").type(IssueTypeModel.POTHOLE)
				.status(IssueStatusModel.OPEN).description("Large pothole causing traffic issues")
				.createdAt(LocalDateTime.parse("2025-12-25T10:00:00"))
				.updatedAt(LocalDateTime.parse("2025-12-25T10:00:00")).userEntity(user).location(location).build();
		IssueEntity issue2 = IssueEntity.builder().id(2L).key("JPR-002").type(IssueTypeModel.WASTE)
				.status(IssueStatusModel.OPEN).description("Blocked drainage system")
				.createdAt(LocalDateTime.parse("2025-12-26T18:00:00"))
				.updatedAt(LocalDateTime.parse("2025-12-26T18:00:00")).userEntity(user).location(location).build();
		MediaEntity media1 = MediaEntity.builder().id(1L).issue(issue1).type(MediaTypeModel.PHOTO)
				.url("https://example.com/photo1.jpg").location(location).build();
		MediaEntity media2 = MediaEntity.builder().id(2L).issue(issue1).type(MediaTypeModel.PHOTO)
				.url("https://example.com/photo2.jpg").location(location).build();
		MediaEntity media3 = MediaEntity.builder().id(3L).issue(issue2).type(MediaTypeModel.PHOTO)
				.url("https://example.com/photo3.jpg").location(location).build();

		double testLat = 26.9124;
		double testLng = 75.8073;
		double radiusMeters = 5000.0;

		when(issueRepository.findByStatusInAndCreatedAtAfterAndWithinRadius(
				Mockito.eq(List.of(IssueStatusModel.OPEN.name(), IssueStatusModel.ONHOLD.name(),
						IssueStatusModel.PENDING.name())),
				Mockito.any(LocalDateTime.class), Mockito.eq(testLat), Mockito.eq(testLng), Mockito.eq(radiusMeters)))
				.thenReturn(List.of(issue2, issue1));
		when(mediaRepository.findByIssue(issue1)).thenReturn(List.of(media1, media2));
		when(mediaRepository.findByIssue(issue2)).thenReturn(List.of(media3));
		when(gcsService.generateSignedUrl(Mockito.anyString())).thenAnswer(invocation -> invocation.getArgument(0));

		// Act
		APIResponse actualResponse = controller.getIssuesNearby(testLat, testLng);

		// Assert
		assertNotNull(actualResponse);
		assertNotNull(actualResponse.getData());
		assertNotNull(actualResponse.getData().getIssues());
		assertEquals(2, actualResponse.getData().getIssues().size());
		var firstIssue = actualResponse.getData().getIssues().get(0);
		assertEquals(2L, firstIssue.getId());
		assertEquals("john_doe", firstIssue.getUser().getUsername());
		assertEquals("waste", firstIssue.getType());
		assertEquals("Blocked drainage system", firstIssue.getDescription());
		assertEquals(1, firstIssue.getMediaUrls().size());
		assertEquals(26.9124, firstIssue.getLocation().getLat());
		assertEquals(75.8073, firstIssue.getLocation().getLng());

		var secondIssue = actualResponse.getData().getIssues().get(1);
		assertEquals(1L, secondIssue.getId());
		assertEquals("john_doe", secondIssue.getUser().getUsername());
		assertEquals("pothole", secondIssue.getType());
		assertEquals(2, secondIssue.getMediaUrls().size());
	}

	@Test
	void getIssuesNearby_shouldReturnEmptyListWhenNoIssuesNearby() {
		// Arrange
		double testLat = 26.9124;
		double testLng = 75.8073;
		double radiusMeters = 5000.0;

		when(issueRepository.findByStatusInAndCreatedAtAfterAndWithinRadius(
				Mockito.eq(List.of(IssueStatusModel.OPEN.name(), IssueStatusModel.ONHOLD.name(),
						IssueStatusModel.PENDING.name())),
				Mockito.any(LocalDateTime.class), Mockito.eq(testLat), Mockito.eq(testLng), Mockito.eq(radiusMeters)))
				.thenReturn(List.of());

		// Act
		APIResponse actualResponse = controller.getIssuesNearby(testLat, testLng);

		// Assert
		assertNotNull(actualResponse);
		assertNotNull(actualResponse.getData());
		assertNotNull(actualResponse.getData().getIssues());
		assertEquals(0, actualResponse.getData().getIssues().size());
	}
}
