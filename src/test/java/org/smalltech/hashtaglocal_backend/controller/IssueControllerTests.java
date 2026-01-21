package org.smalltech.hashtaglocal_backend.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.smalltech.hashtaglocal_backend.entity.IssueEntity;
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
		entity.setType(IssueTypeModel.Pothole);
		entity.setDescription("Large pothole causing traffic issues");
		entity.setStatus(IssueStatusModel.OPEN);
		entity.setCreatedAt(LocalDateTime.parse("2025-12-26T18:00:00"));

		when(issueRepository.findById(issueId)).thenReturn(Optional.of(entity));
		// Also mock fallback issue if needed
		IssueEntity fallbackEntity = new IssueEntity();
		fallbackEntity.setId(1L);
		fallbackEntity.setType(IssueTypeModel.Pothole);
		fallbackEntity.setDescription("Fallback issue");
		fallbackEntity.setStatus(IssueStatusModel.OPEN);
		fallbackEntity.setCreatedAt(LocalDateTime.parse("2025-12-26T18:00:00"));

		when(issueRepository.findById(1L)).thenReturn(Optional.of(fallbackEntity));

		// Act
		APIResponse actualResponse = controller.getIssue(issueId);
		APIResponse expectedResponse = expectedMockResponse(entity);

		// Assert
		assertEquals(expectedResponse, actualResponse);
	}

	private APIResponse expectedMockResponse(IssueEntity entity) {
		User user = User.builder().username("john_doe").profilePhoto("https://example.com/profile.jpg").build();

		Locality locality = Locality.builder().hashtags(List.of("#Jaipur")).build();

		Location location = Location.builder().lat("12.34").lng("56.78").locality(locality)
				.address("Sector 3, Jawahar Nagar").colloquialName("Near Patrika Gate").build();

		Media media1 = Media.builder().location(location).type("photo")
				.url("https://sripath.com/wp-content/uploads/2025/01/iStock-174662203.jpg").build();

		Media media2 = Media.builder().location(location).type("photo")
				.url("https://nub.news/api/image/526263/article.png").build();

		ViewerContext viewerContext = ViewerContext.builder().upvote(true).build();

		Issue issue = Issue.builder().user(user).location(location).type(entity.getType().name())
				.description(entity.getDescription()).createdAt(entity.getCreatedAt())
				.mediaUrls(List.of(media1, media2)).voteCount(42).verifyCount(10).status(entity.getStatus().name())
				.rank(1).viewerContext(viewerContext).build();

		ResponseData data = ResponseData.builder().issue(issue).build();

		return APIResponse.builder().data(data).build();
	}

}
