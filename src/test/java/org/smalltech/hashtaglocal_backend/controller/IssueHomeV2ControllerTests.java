package org.smalltech.hashtaglocal_backend.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.smalltech.hashtaglocal_backend.model.APIResponse;
import org.smalltech.hashtaglocal_backend.model.Issue;
import org.smalltech.hashtaglocal_backend.model.Location;
import org.smalltech.hashtaglocal_backend.model.ResponseData;
import org.smalltech.hashtaglocal_backend.model.User;
import org.smalltech.hashtaglocal_backend.model.ViewerContext;
import org.smalltech.hashtaglocal_backend.service.IssueHomeAssembler;

class IssueHomeV2ControllerTests {

	private IssueHomeAssembler issueHomeAssembler;
	private IssueHomeV2Controller controller;

	@BeforeEach
	void setup() {
		issueHomeAssembler = Mockito.mock(IssueHomeAssembler.class);
		controller = new IssueHomeV2Controller(issueHomeAssembler);
	}

	@Test
	void getIssuesNearby_shouldReturnIssues() {
		// Arrange
		double testLat = 26.9124;
		double testLng = 75.8073;

		Issue issue1 = Issue.builder().id(1L).type("pothole").description("Large pothole")
				.user(User.builder().username("john_doe").build())
				.location(Location.builder().lat(26.9124).lng(75.8073).build())
				.viewerContext(ViewerContext.builder().upvote(false).build()).createdAt(LocalDateTime.now())
				.mediaUrls(List.of()).build();

		Issue issue2 = Issue.builder().id(2L).type("waste").description("Blocked drainage")
				.user(User.builder().username("john_doe").build())
				.location(Location.builder().lat(26.9124).lng(75.8073).build())
				.viewerContext(ViewerContext.builder().upvote(false).build()).createdAt(LocalDateTime.now())
				.mediaUrls(List.of()).build();

		APIResponse response = APIResponse.builder()
				.data(ResponseData.builder().issues(List.of(issue2, issue1)).build()).build();

		when(issueHomeAssembler.getNearby(testLat, testLng)).thenReturn(response);

		// Act
		APIResponse actual = controller.getIssuesNearby(testLat, testLng);

		// Assert
		assertNotNull(actual);
		assertNotNull(actual.getData());
		assertEquals(2, actual.getData().getIssues().size());
		assertEquals(2L, actual.getData().getIssues().get(0).getId());
		assertEquals(1L, actual.getData().getIssues().get(1).getId());
	}

	@Test
	void getIssuesNearby_shouldReturnEmptyList() {
		double testLat = 26.9124;
		double testLng = 75.8073;

		APIResponse response = APIResponse.builder().data(ResponseData.builder().issues(List.of()).build()).build();

		when(issueHomeAssembler.getNearby(testLat, testLng)).thenReturn(response);

		APIResponse actual = controller.getIssuesNearby(testLat, testLng);

		assertNotNull(actual);
		assertNotNull(actual.getData());
		assertEquals(0, actual.getData().getIssues().size());
	}
}
