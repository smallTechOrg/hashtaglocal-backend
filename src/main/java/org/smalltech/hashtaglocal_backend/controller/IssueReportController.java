package org.smalltech.hashtaglocal_backend.controller;

import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.smalltech.hashtaglocal_backend.model.APIResponse;
import org.smalltech.hashtaglocal_backend.model.ResponseData;
import org.smalltech.hashtaglocal_backend.model.request.IssueReportRequest;
import org.smalltech.hashtaglocal_backend.service.IssueReportService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/issue")
@SecurityRequirement(name = "bearerAuth")
@RequiredArgsConstructor
public class IssueReportController {

	private final IssueReportService issueReportService;

	@PostMapping
	public ResponseEntity<APIResponse> createIssue(@AuthenticationPrincipal Long userId,
			@Valid @RequestBody IssueReportRequest request) {
		Long issueId = issueReportService.createIssue(userId, request);

		APIResponse response = APIResponse.builder().data(ResponseData.builder().issueId(issueId).build()).build();

		return ResponseEntity.ok(response);
	}
}
