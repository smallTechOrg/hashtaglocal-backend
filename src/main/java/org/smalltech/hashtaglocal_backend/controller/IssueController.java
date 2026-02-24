package org.smalltech.hashtaglocal_backend.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.smalltech.hashtaglocal_backend.mapper.IssueViewMapper;
import org.smalltech.hashtaglocal_backend.model.APIResponse;
import org.smalltech.hashtaglocal_backend.model.ResponseData;
import org.smalltech.hashtaglocal_backend.model.request.IssuePatchRequest;
import org.smalltech.hashtaglocal_backend.model.request.IssueReportRequest;
import org.smalltech.hashtaglocal_backend.model.request.IssueVerifyRequest;
import org.smalltech.hashtaglocal_backend.service.IssueActionService;
import org.smalltech.hashtaglocal_backend.service.IssueHomeService;
import org.smalltech.hashtaglocal_backend.service.IssuePatchService;
import org.smalltech.hashtaglocal_backend.service.IssueQueryService;
import org.smalltech.hashtaglocal_backend.service.IssueReportService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
@Tag(name = "Issue", description = "issue APIs")
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class IssueController {

	private final IssueActionService issueActionService;
	private final IssueHomeService issueHomeAssembler;
	private final IssuePatchService issuePatchService;
	private final IssueQueryService issueQueryService;
	private final IssueReportService issueReportService;
	private final IssueViewMapper issueViewMapper;

	@GetMapping("/issue/{issueId}")
	@Operation(summary = "Get issue", description = "Returns a issue response with user, location, locality and viewer context.")
	@ApiResponse(responseCode = "200", description = "Successful issue response", content = @Content(mediaType = "application/json", schema = @Schema(implementation = APIResponse.class)))
	public APIResponse getIssue(@PathVariable Long issueId) {
		var issueEntity = issueQueryService.get(issueId);

		var issue = issueViewMapper.map(issueEntity);
		return APIResponse.builder().data(ResponseData.builder().issue(issue).build()).build();
	}

	@PatchMapping("/issue/{issueId}")
	@Transactional
	@Operation(summary = "Update issue", description = "Patch issue fields like status, type, description, and coordinates.")
	@ApiResponse(responseCode = "200", description = "Issue patched successfully", content = @Content(mediaType = "application/json", schema = @Schema(implementation = APIResponse.class)))
	public ResponseEntity<APIResponse> patchIssue(@PathVariable Long issueId,
			@Valid @RequestBody IssuePatchRequest request) {
		var issueEntity = issuePatchService.patchIssue(issueId, request);

		var issue = issueViewMapper.map(issueEntity);
		return ResponseEntity.ok(APIResponse.builder().data(ResponseData.builder().issue(issue).build()).build());
	}

	@PutMapping("/issue/{issueId}")
	@SecurityRequirement(name = "bearerAuth")
	@Operation(summary = "Verify or resolve issue", description = "Verify an issue with media attachments and create verification records.")
	@ApiResponse(responseCode = "200", description = "Issue verified successfully", content = @Content(mediaType = "application/json", schema = @Schema(implementation = APIResponse.class)))
	public ResponseEntity<APIResponse> verifyIssue(@PathVariable Long issueId, @AuthenticationPrincipal Long userId,
			@Valid @RequestBody IssueVerifyRequest request) {
		Long updatedIssueId = issueActionService.handle(issueId, userId, request);

		APIResponse response = APIResponse.builder().data(ResponseData.builder().issueId(updatedIssueId).build())
				.build();

		return ResponseEntity.ok(response);
	}

	@GetMapping("/issues")
	@Operation(summary = "Get issue Home", description = "Returns a List of issues with user, location, locality and viewer context. Optionally filter by locality hashtag.")
	@ApiResponse(responseCode = "200", description = "Successful issue response", content = @Content(mediaType = "application/json", schema = @Schema(implementation = APIResponse.class)))
	public APIResponse getIssues(@RequestParam(value = "locality", required = false) String localityHashtag) {
		return issueHomeAssembler.getHome(localityHashtag);
	}

	@PostMapping("/issue")
	@SecurityRequirement(name = "bearerAuth")
	@Operation(summary = "Create issue", description = "Creates a new issue with the given details.")
	@ApiResponse(responseCode = "200", description = "Issue created successfully", content = @Content(mediaType = "application/json", schema = @Schema(implementation = APIResponse.class)))
	public ResponseEntity<APIResponse> createIssue(@AuthenticationPrincipal Long userId,
			@Valid @RequestBody IssueReportRequest request) {
		Long issueId = issueReportService.createIssue(userId, request);

		APIResponse response = APIResponse.builder().data(ResponseData.builder().issueId(issueId).build()).build();

		return ResponseEntity.ok(response);
	}

}
