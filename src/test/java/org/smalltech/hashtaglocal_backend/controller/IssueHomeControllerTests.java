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
import org.smalltech.hashtaglocal_backend.model.ResponseData;
import org.smalltech.hashtaglocal_backend.service.IssueHomeService;

class IssueHomeControllerTests {

	private IssueHomeService issueHomeAssembler;
	private IssueHomeController controller;

	@BeforeEach
	void setup() {
		issueHomeAssembler = Mockito.mock(IssueHomeService.class);
		controller = new IssueHomeController(issueHomeAssembler);
	}

	@Test
	void getIssues_shouldReturnApiResponse() {
		Issue issue1 = Issue.builder().id(1L).description("Issue 1").createdAt(LocalDateTime.now()).type("pothole")
				.build();

		Issue issue2 = Issue.builder().id(2L).description("Issue 2").createdAt(LocalDateTime.now()).type("garbage")
				.build();

		APIResponse mockResponse = APIResponse.builder()
				.data(ResponseData.builder().issues(List.of(issue1, issue2)).build()).build();

		when(issueHomeAssembler.getHome(null)).thenReturn(mockResponse);

		APIResponse actual = controller.getIssues(null);

		assertNotNull(actual);
		assertNotNull(actual.getData());
		assertEquals(2, actual.getData().getIssues().size());
		assertEquals(1L, actual.getData().getIssues().get(0).getId());
		assertEquals(2L, actual.getData().getIssues().get(1).getId());
	}

	@Test
	void getIssues_shouldPassLocalityFilter() {
		APIResponse mockResponse = APIResponse.builder().data(ResponseData.builder().issues(List.of()).build()).build();

		when(issueHomeAssembler.getHome("Jaipur")).thenReturn(mockResponse);

		APIResponse actual = controller.getIssues("Jaipur");

		assertNotNull(actual);
		assertNotNull(actual.getData());
		assertEquals(0, actual.getData().getIssues().size());
	}
}
