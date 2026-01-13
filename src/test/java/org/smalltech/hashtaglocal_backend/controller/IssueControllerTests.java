package org.smalltech.hashtaglocal_backend.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
<<<<<<< HEAD
import static org.mockito.Mockito.*;

import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.smalltech.hashtaglocal_backend.entity.IssueEntity;
=======

import java.util.List;
import org.junit.jupiter.api.Test;
>>>>>>> staging
import org.smalltech.hashtaglocal_backend.model.APIResponse;
import org.smalltech.hashtaglocal_backend.model.Issue;
import org.smalltech.hashtaglocal_backend.model.Locality;
import org.smalltech.hashtaglocal_backend.model.Location;
import org.smalltech.hashtaglocal_backend.model.Media;
import org.smalltech.hashtaglocal_backend.model.ResponseData;
import org.smalltech.hashtaglocal_backend.model.User;
import org.smalltech.hashtaglocal_backend.model.ViewerContext;
<<<<<<< HEAD
import org.smalltech.hashtaglocal_backend.repository.IssueRepository;

class IssueControllerTests {

	private IssueRepository issueRepository;
	private IssueController controller;

	@BeforeEach
	void setup() {
		issueRepository = Mockito.mock(IssueRepository.class);
		controller = new IssueController(issueRepository);
	}

	@Test
	void getIssue_shouldReturnValidApiResponse() {
		// Arrange
		Long issueId = 5L;

		// Mocked entity returned by repository
		IssueEntity entity = new IssueEntity();
		entity.setId(issueId);
		entity.setType("pothole");
		entity.setDescription("Large pothole causing traffic issues");
		entity.setStatus("OPEN");
		entity.setCreatedAt("2025-12-26T18:00:00");

		when(issueRepository.findById(issueId)).thenReturn(Optional.of(entity));
		// Also mock fallback issue if needed
		IssueEntity fallbackEntity = new IssueEntity();
		fallbackEntity.setId(1L);
		fallbackEntity.setType("default");
		fallbackEntity.setDescription("Fallback issue");
		fallbackEntity.setStatus("OPEN");
		fallbackEntity.setCreatedAt("2025-12-26T18:00:00");

		when(issueRepository.findById(1L)).thenReturn(Optional.of(fallbackEntity));

		// Act
		APIResponse actualResponse = controller.getIssue(issueId);
		APIResponse expectedResponse = expectedMockResponse(entity);
=======

class IssueControllerTests {
	@Test
	void getIssue_shouldReturnValidApiResponse() {
		// Arrange
		IssueController controller = new IssueController();

		// Act
		APIResponse actualResponse = controller.getIssue();
		APIResponse expectedResponse = expectedMockResponse();
>>>>>>> staging

		// Assert
		assertEquals(expectedResponse, actualResponse);
	}

<<<<<<< HEAD
	private APIResponse expectedMockResponse(IssueEntity entity) {
=======
	private APIResponse expectedMockResponse() {
>>>>>>> staging
		User user = new User("john_doe", "https://example.com/profile.jpg");
		Locality locality = new Locality(List.of("#Jaipur"));
		Location location = new Location("12.34", "56.78", locality, "Sector 3, Jawahar Nagar", "Near Patrika Gate");
		Media media1 = new Media(location, "photo",
				"https://sripath.com/wp-content/uploads/2025/01/iStock-174662203.jpg");
		Media media2 = new Media(location, "photo", "https://nub.news/api/image/526263/article.png");
		ViewerContext viewerContext = new ViewerContext(true);
<<<<<<< HEAD
		Issue issue = new Issue(user, location, entity.getType(), entity.getDescription(), entity.getCreatedAt(),
				List.of(media1, media2), 42, 10, entity.getStatus(), 1, viewerContext);
=======
		Issue issue = new Issue(user, location, "pothole", "Large pothole causing traffic issues",
				"2025-12-26T18:00:00", List.of(media1, media2), 42, 10, "OPEN", 1, viewerContext);
>>>>>>> staging
		ResponseData data = new ResponseData(issue);

		return new APIResponse(data);
	}

}
