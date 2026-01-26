package org.smalltech.hashtaglocal_backend.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.Point;
import org.locationtech.jts.geom.Polygon;
import org.mockito.Mockito;
import org.smalltech.hashtaglocal_backend.dto.LocationMetadataDTO;
import org.smalltech.hashtaglocal_backend.entity.IssueEntity;
import org.smalltech.hashtaglocal_backend.entity.Locality;
import org.smalltech.hashtaglocal_backend.entity.Location;
import org.smalltech.hashtaglocal_backend.entity.MediaEntity;
import org.smalltech.hashtaglocal_backend.entity.UserEntity;
import org.smalltech.hashtaglocal_backend.model.APIResponse;
import org.smalltech.hashtaglocal_backend.model.IssueStatusModel;
import org.smalltech.hashtaglocal_backend.model.IssueTypeModel;
import org.smalltech.hashtaglocal_backend.model.MediaTypeModel;
import org.smalltech.hashtaglocal_backend.model.request.IssuePatchRequest;
import org.smalltech.hashtaglocal_backend.repository.IssueRepository;
import org.smalltech.hashtaglocal_backend.repository.MediaRepository;
import org.smalltech.hashtaglocal_backend.service.GCSService;
import org.smalltech.hashtaglocal_backend.service.GoogleMapsGeocodingService;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

class IssueControllerTests {

	private IssueRepository issueRepository;
	private MediaRepository mediaRepository;
	private GCSService gcsService;
	private GoogleMapsGeocodingService googleMapsGeocodingService;
	private IssueController controller;

	@BeforeEach
	void setup() {
		issueRepository = Mockito.mock(IssueRepository.class);
		mediaRepository = Mockito.mock(MediaRepository.class);
		gcsService = Mockito.mock(GCSService.class);
		googleMapsGeocodingService = Mockito.mock(GoogleMapsGeocodingService.class);
		controller = new IssueController(issueRepository, mediaRepository, gcsService, googleMapsGeocodingService);
	}

	@Test
	void getIssue_shouldReturnValidApiResponse() {
		// Arrange
		Long issueId = 1L;
		GeometryFactory geometryFactory = new GeometryFactory();

		// Create test data
		UserEntity user = UserEntity.builder().id(1L).username("john_doe")
				.profilePicture("https://example.com/profile.jpg").locale("en_US").build();

		Polygon polygon = geometryFactory
				.createPolygon(new Coordinate[]{new Coordinate(75.7, 26.8), new Coordinate(75.9, 26.8),
						new Coordinate(75.9, 27.0), new Coordinate(75.7, 27.0), new Coordinate(75.7, 26.8)});

		Locality locality = Locality.builder().id(1L).hashtag("Jaipur").name("Jaipur").geoBoundary(polygon).build();

		Point point = geometryFactory.createPoint(new Coordinate(56.78, 12.34));
		Map<String, Object> metaData = new HashMap<>();
		metaData.put("address", "Sector 3, Jawahar Nagar");
		metaData.put("colloquialName", "Near Patrika Gate");

		Location location = Location.builder().id(1L).point(point).locality(locality).name("Sector 3, Jawahar Nagar")
				.metaData(metaData).build();

		IssueEntity entity = IssueEntity.builder().id(issueId).key("JPR-001").type(IssueTypeModel.POTHOLE)
				.status(IssueStatusModel.OPEN).description("Large pothole causing traffic issues")
				.createdAt(LocalDateTime.parse("2025-12-26T18:00:00"))
				.updatedAt(LocalDateTime.parse("2025-12-26T18:00:00")).userEntity(user).location(location).build();

		MediaEntity media1 = MediaEntity.builder().id(1L).issue(entity).type(MediaTypeModel.PHOTO)
				.url("https://sripath.com/wp-content/uploads/2025/01/iStock-174662203.jpg").location(location).build();

		MediaEntity media2 = MediaEntity.builder().id(2L).issue(entity).type(MediaTypeModel.PHOTO)
				.url("https://nub.news/api/image/526263/article.png").location(location).build();

		when(issueRepository.findById(issueId)).thenReturn(Optional.of(entity));
		when(mediaRepository.findByIssue(entity)).thenReturn(List.of(media1, media2));

		// Act
		APIResponse actualResponse = controller.getIssue(issueId);

		// Assert
		assertNotNull(actualResponse);
		assertNotNull(actualResponse.getData());
		assertNotNull(actualResponse.getData().getIssue());
		assertEquals(issueId, actualResponse.getData().getIssue().getId());
		assertEquals("john_doe", actualResponse.getData().getIssue().getUser().getUsername());
		assertEquals("pothole", actualResponse.getData().getIssue().getType());
		assertEquals("Large pothole causing traffic issues", actualResponse.getData().getIssue().getDescription());
		assertEquals(2, actualResponse.getData().getIssue().getMediaUrls().size());
		assertEquals(0, actualResponse.getData().getIssue().getVoteCount());
		assertEquals(0, actualResponse.getData().getIssue().getVerifyCount());
	}

	@Test
	void patchIssue_shouldUpdateProvidedFields() {
		Long issueId = 2L;
		LocalDateTime originalUpdatedAt = LocalDateTime.now().minusHours(4);

		UserEntity user = UserEntity.builder().id(2L).username("alice").profilePicture("https://example.com/p.png")
				.locale("en_IN").build();

		IssueEntity entity = IssueEntity.builder().id(issueId).type(IssueTypeModel.POTHOLE)
				.status(IssueStatusModel.OPEN).description("Original description")
				.createdAt(LocalDateTime.parse("2025-12-20T09:15:00")).updatedAt(originalUpdatedAt).userEntity(user)
				.build();

		when(issueRepository.findById(issueId)).thenReturn(Optional.of(entity));
		when(mediaRepository.findByIssue(entity)).thenReturn(List.of());
		when(issueRepository.save(entity)).thenReturn(entity);

		IssuePatchRequest request = new IssuePatchRequest();
		request.setStatus("RESOLVED");
		request.setType("WASTE");
		request.setDescription("Updated description");

		var response = controller.patchIssue(issueId, request);

		assertEquals(HttpStatus.OK, response.getStatusCode());
		assertNotNull(response.getBody());
		assertNotNull(response.getBody().getData());
		assertNotNull(response.getBody().getData().getIssue());
		assertEquals("waste", response.getBody().getData().getIssue().getType());
		assertEquals("Updated description", response.getBody().getData().getIssue().getDescription());
		assertEquals(IssueStatusModel.RESOLVED, entity.getStatus());
		assertEquals(IssueTypeModel.WASTE, entity.getType());
		assertNotNull(entity.getUpdatedAt());
		assertTrue(entity.getUpdatedAt().isAfter(originalUpdatedAt));
		assertEquals(issueId, response.getBody().getData().getIssue().getId());
		verify(issueRepository, times(1)).save(entity);
	}

	@Test
	void patchIssue_withInvalidStatusShouldThrowBadRequest() {
		Long issueId = 3L;

		IssueEntity entity = IssueEntity.builder().id(issueId).type(IssueTypeModel.SAFETY).status(IssueStatusModel.OPEN)
				.description("Safety issue").createdAt(LocalDateTime.parse("2025-10-01T10:00:00"))
				.updatedAt(LocalDateTime.now()).build();

		when(issueRepository.findById(issueId)).thenReturn(Optional.of(entity));

		IssuePatchRequest request = new IssuePatchRequest();
		request.setStatus("NOT_A_STATUS");

		assertThrows(ResponseStatusException.class, () -> controller.patchIssue(issueId, request));
		verify(issueRepository, times(0)).save(Mockito.any());
	}

	@Test
	void patchIssue_shouldUpdateLatLng() {
		Long issueId = 4L;
		LocalDateTime originalUpdatedAt = LocalDateTime.now().minusHours(2);

		GeometryFactory geometryFactory = new GeometryFactory();
		UserEntity user = UserEntity.builder().id(4L).username("bob").profilePicture("https://example.com/bob.png")
				.locale("en_US").build();

		Point oldPoint = geometryFactory.createPoint(new Coordinate(77.5, 12.5));
		Locality locality = Locality.builder().id(1L).hashtag("Bangalore").build();
		Location location = Location.builder().id(4L).point(oldPoint).locality(locality).name("Old Location")
				.metaData(new HashMap<>()).build();

		IssueEntity entity = IssueEntity.builder().id(issueId).type(IssueTypeModel.POTHOLE)
				.status(IssueStatusModel.OPEN).description("Location test").location(location)
				.createdAt(LocalDateTime.parse("2025-12-20T09:15:00")).updatedAt(originalUpdatedAt).userEntity(user)
				.build();

		// Mock Google Maps geocoding response
		LocationMetadataDTO mockMetadata = LocationMetadataDTO.builder().name("Indiranagar, Bangalore")
				.formattedAddress("Indiranagar, Bengaluru, Karnataka 560038, India").city("Bangalore")
				.region("Karnataka").country("India").build();

		when(issueRepository.findById(issueId)).thenReturn(Optional.of(entity));
		when(mediaRepository.findByIssue(entity)).thenReturn(List.of());
		when(issueRepository.save(entity)).thenReturn(entity);
		when(googleMapsGeocodingService.reverseGeocode(12.945722, 77.675312)).thenReturn(mockMetadata);

		IssuePatchRequest request = new IssuePatchRequest();
		request.setLat(12.945722);
		request.setLng(77.675312);

		var response = controller.patchIssue(issueId, request);

		assertEquals(HttpStatus.OK, response.getStatusCode());
		assertNotNull(response.getBody());
		assertEquals(12.945722, entity.getLocation().getPoint().getY());
		assertEquals(77.675312, entity.getLocation().getPoint().getX());
		assertEquals("Indiranagar, Bangalore", entity.getLocation().getName());
		assertNotNull(entity.getLocation().getMetaData());
		assertNotNull(entity.getUpdatedAt());
		assertTrue(entity.getUpdatedAt().isAfter(originalUpdatedAt));
		verify(issueRepository, times(1)).save(entity);
		verify(googleMapsGeocodingService, times(1)).reverseGeocode(12.945722, 77.675312);
	}
}
