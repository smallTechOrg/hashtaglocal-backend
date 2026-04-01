package org.smalltech.hashtaglocal_backend.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.smalltech.hashtaglocal_backend.mapper.IssueViewMapper;
import org.smalltech.hashtaglocal_backend.model.NewAPIResponse;
import org.smalltech.hashtaglocal_backend.model.request.IssuePatchRequest;
import org.smalltech.hashtaglocal_backend.model.request.IssueReportRequest;
import org.smalltech.hashtaglocal_backend.model.request.IssueVerifyRequest;
import org.smalltech.hashtaglocal_backend.model.response.IssueActionResponseData;
import org.smalltech.hashtaglocal_backend.model.response.IssueListResponseData;
import org.smalltech.hashtaglocal_backend.model.response.IssueResponseData;
import org.smalltech.hashtaglocal_backend.model.response.IssueStoriesResponseData;
import org.smalltech.hashtaglocal_backend.service.IssueActionService;
import org.smalltech.hashtaglocal_backend.service.IssueHomeService;
import org.smalltech.hashtaglocal_backend.service.IssuePatchService;
import org.smalltech.hashtaglocal_backend.service.IssueQueryService;
import org.smalltech.hashtaglocal_backend.service.IssueReportService;
import org.smalltech.hashtaglocal_backend.service.IssueStoryService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
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
public class IssueController {

  private final IssueActionService issueActionService;
  private final IssueHomeService issueHomeAssembler;
  private final IssuePatchService issuePatchService;
  private final IssueQueryService issueQueryService;
  private final IssueReportService issueReportService;
  private final IssueStoryService issueStoryService;
  private final IssueViewMapper issueViewMapper;

  @GetMapping("/issue/{issueId}")
  @Operation(
      summary = "Get issue",
      description =
          "Returns an issue with user, location, locality and viewer context. ONHOLD issues are"
              + " only returned to the issue owner; all other callers receive 404.")
  public ResponseEntity<NewAPIResponse<IssueResponseData>> getIssue(
      @PathVariable Long issueId, @AuthenticationPrincipal Long viewerUserId) {
    var issueEntity = issueQueryService.get(issueId, viewerUserId);

    IssueResponseData issueResponse = issueViewMapper.map(issueEntity, viewerUserId);

    return ResponseEntity.ok(
        NewAPIResponse.<IssueResponseData>builder().data(issueResponse).build());
  }

  @PatchMapping("/issue/{issueId}")
  @Operation(
      summary = "Update issue",
      description = "Patch issue fields like status, type, description, and coordinates.")
  public ResponseEntity<NewAPIResponse<IssueResponseData>> patchIssue(
      @PathVariable Long issueId, @Valid @RequestBody IssuePatchRequest request) {
    var issueEntity = issuePatchService.patchIssue(issueId, request);

    IssueResponseData issueResponse = issueViewMapper.map(issueEntity);

    return ResponseEntity.ok(
        NewAPIResponse.<IssueResponseData>builder().data(issueResponse).build());
  }

  @PutMapping("/issue/{issueId}")
  @SecurityRequirement(name = "bearerAuth")
  @Operation(
      summary = "Verify or resolve issue",
      description = "Verify an issue with media attachments and create verification records.")
  public ResponseEntity<NewAPIResponse<IssueActionResponseData>> verifyIssue(
      @PathVariable Long issueId,
      @AuthenticationPrincipal Long userId,
      @Valid @RequestBody IssueVerifyRequest request) {
    Long updatedIssueId = issueActionService.handle(issueId, userId, request);

    NewAPIResponse<IssueActionResponseData> response =
        NewAPIResponse.<IssueActionResponseData>builder()
            .data(IssueActionResponseData.builder().issueId(updatedIssueId).build())
            .build();

    return ResponseEntity.ok(response);
  }

  @GetMapping("/issues")
  @Operation(
      summary = "Get issue Home",
      description =
          "Returns a List of issues with user, location, locality and viewer context. Optionally filter by locality hashtag.")
  public NewAPIResponse<IssueListResponseData> getIssues(
      @AuthenticationPrincipal Long viewerUserId,
      @RequestParam(value = "locality", required = false) String localityHashtag) {
    return issueHomeAssembler.getHome(localityHashtag, viewerUserId);
  }

  @GetMapping("/issues/stories")
  @Operation(
      summary = "Get resolved issue stories",
      description =
          "Returns resolved issues with timeline data showing how they progressed from reported to resolved. Optionally filter by locality hashtag.")
  public NewAPIResponse<IssueStoriesResponseData> getIssueStories(
      @RequestParam(value = "locality", required = false) String localityHashtag,
      @RequestParam(value = "limit", required = false, defaultValue = "5") int limit) {
    var stories = issueStoryService.getStories(localityHashtag, limit);
    return NewAPIResponse.<IssueStoriesResponseData>builder()
        .data(IssueStoriesResponseData.builder().stories(stories).build())
        .build();
  }

  @PostMapping("/issue")
  @SecurityRequirement(name = "bearerAuth")
  @Operation(summary = "Create issue", description = "Creates a new issue with the given details.")
  public ResponseEntity<NewAPIResponse<IssueActionResponseData>> createIssue(
      @AuthenticationPrincipal Long userId, @Valid @RequestBody IssueReportRequest request) {
    Long issueId = issueReportService.createIssue(userId, request);

    NewAPIResponse<IssueActionResponseData> response =
        NewAPIResponse.<IssueActionResponseData>builder()
            .data(IssueActionResponseData.builder().issueId(issueId).build())
            .build();

    return ResponseEntity.ok(response);
  }
}
