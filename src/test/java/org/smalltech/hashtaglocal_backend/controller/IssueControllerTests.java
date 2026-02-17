package org.smalltech.hashtaglocal_backend.controller;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.smalltech.hashtaglocal_backend.entity.IssueEntity;
import org.smalltech.hashtaglocal_backend.mapper.IssueViewMapper;
import org.smalltech.hashtaglocal_backend.model.APIResponse;
import org.smalltech.hashtaglocal_backend.model.Issue;
import org.smalltech.hashtaglocal_backend.model.IssueStatusModel;
import org.smalltech.hashtaglocal_backend.model.IssueTypeModel;
import org.smalltech.hashtaglocal_backend.model.request.IssuePatchRequest;
import org.smalltech.hashtaglocal_backend.model.request.IssueVerifyRequest;
import org.smalltech.hashtaglocal_backend.service.IssueActionService;
import org.smalltech.hashtaglocal_backend.service.IssuePatchService;
import org.smalltech.hashtaglocal_backend.service.IssueQueryService;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

class IssueControllerTests {

	private IssueQueryService issueQueryService;
	private IssuePatchService issuePatchService;
	private IssueActionService issueActionService;
	private IssueViewMapper issueViewMapper;
	private IssueController controller;

	@BeforeEach
	void setup() {
		issueQueryService = Mockito.mock(IssueQueryService.class);
		issuePatchService = Mockito.mock(IssuePatchService.class);
		issueActionService = Mockito.mock(IssueActionService.class);
		issueViewMapper = Mockito.mock(IssueViewMapper.class);

		controller = new IssueController(issueQueryService, issuePatchService, issueActionService, issueViewMapper);
	}

	@Test
	void getIssue_shouldReturnValidApiResponse() {
		Long issueId = 1L;

		IssueEntity entity = IssueEntity.builder().id(issueId).type(IssueTypeModel.POTHOLE)
				.status(IssueStatusModel.OPEN).description("Large pothole causing traffic issues")
				.createdAt(LocalDateTime.parse("2025-12-26T18:00:00"))
				.updatedAt(LocalDateTime.parse("2025-12-26T18:00:00")).build();

		Issue view = Issue.builder().id(issueId).build();

		when(issueQueryService.get(issueId)).thenReturn(entity);
		when(issueViewMapper.map(entity)).thenReturn(view);

		APIResponse response = controller.getIssue(issueId);

		assertNotNull(response);
		assertNotNull(response.getData());
		assertNotNull(response.getData().getIssue());
		assertEquals(issueId, response.getData().getIssue().getId());

		verify(issueQueryService, times(1)).get(issueId);
		verify(issueViewMapper, times(1)).map(entity);
	}

	@Test
	void patchIssue_shouldUpdateProvidedFields() {
		Long issueId = 2L;

		IssueEntity entity = IssueEntity.builder().id(issueId).type(IssueTypeModel.POTHOLE)
				.status(IssueStatusModel.OPEN).description("Original description")
				.createdAt(LocalDateTime.parse("2025-12-20T09:15:00")).build();

		Issue view = Issue.builder().id(issueId).build();

		when(issuePatchService.patchIssue(eq(issueId), any(IssuePatchRequest.class))).thenReturn(entity);
		when(issueViewMapper.map(entity)).thenReturn(view);

		IssuePatchRequest request = new IssuePatchRequest();
		request.setStatus("RESOLVED");
		request.setType("WASTE");
		request.setDescription("Updated description");

		var response = controller.patchIssue(issueId, request);

		assertEquals(HttpStatus.OK, response.getStatusCode());
		assertNotNull(response.getBody());
		assertNotNull(response.getBody().getData());
		assertNotNull(response.getBody().getData().getIssue());
		assertEquals(issueId, response.getBody().getData().getIssue().getId());

		verify(issuePatchService, times(1)).patchIssue(eq(issueId), any(IssuePatchRequest.class));
		verify(issueViewMapper, times(1)).map(entity);
	}

	@Test
	void patchIssue_withInvalidStatusShouldThrowBadRequest() {
		Long issueId = 3L;

		when(issuePatchService.patchIssue(eq(issueId), any(IssuePatchRequest.class)))
				.thenThrow(new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid status"));

		IssuePatchRequest request = new IssuePatchRequest();
		request.setStatus("NOT_A_STATUS");

		ResponseStatusException ex = assertThrows(ResponseStatusException.class,
				() -> controller.patchIssue(issueId, request));

		assertEquals(HttpStatus.BAD_REQUEST, ex.getStatusCode());
		verify(issuePatchService, times(1)).patchIssue(eq(issueId), any(IssuePatchRequest.class));
	}

	@Test
	void verifyIssue_shouldDelegateToService() {
		Long issueId = 10L;
		Long userId = 1L;

		when(issueActionService.handle(eq(issueId), eq(userId), any(IssueVerifyRequest.class))).thenReturn(issueId);

		IssueVerifyRequest request = new IssueVerifyRequest();
		var response = controller.verifyIssue(issueId, userId, request);

		assertEquals(HttpStatus.OK, response.getStatusCode());
		assertNotNull(response.getBody());
		assertEquals(issueId, response.getBody().getData().getIssueId());

		verify(issueActionService, times(1)).handle(eq(issueId), eq(userId), any(IssueVerifyRequest.class));
	}
}
