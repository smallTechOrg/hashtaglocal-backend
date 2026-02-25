package org.smalltech.hashtaglocal_backend.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.smalltech.hashtaglocal_backend.model.IssueActionApprovalStatus;
import org.smalltech.hashtaglocal_backend.model.NewAPIResponse;
import org.smalltech.hashtaglocal_backend.model.response.IssueActionAdminResponseData;
import org.smalltech.hashtaglocal_backend.model.response.IssueActionResponseData;
import org.smalltech.hashtaglocal_backend.service.IssueActionAdminService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Admin endpoints for reviewing issue action submissions.
 *
 * <p>All routes under {@code /admin} require the caller to be authenticated as a user with the
 * {@code ADMIN} role.
 *
 * <h2>Approval state machine</h2>
 *
 * <ul>
 *   <li><b>REPORT approved</b> → issue transitions {@code ONHOLD → OPEN}
 *   <li><b>REPORT rejected</b> → issue transitions {@code ONHOLD → REJECTED}
 *   <li><b>VERIFY approved</b> → action marked {@code APPROVED}, linked media approved, verifyCount
 *       incremented
 *   <li><b>VERIFY rejected</b> → action and linked media marked {@code REJECTED}; issue unchanged
 *   <li><b>RESOLVE approved</b> → issue transitions {@code PENDING → RESOLVED}
 *   <li><b>RESOLVE rejected</b> → issue reverts {@code PENDING → OPEN}
 * </ul>
 */
@RestController
@RequestMapping("/admin")
@Tag(
    name = "Admin — Issue Actions",
    description = "Admin APIs for approving and rejecting issue action submissions.")
@SecurityRequirement(name = "bearerAuth")
@RequiredArgsConstructor
public class IssueAdminController {

  private final IssueActionAdminService issueActionAdminService;

  /**
   * Returns all issue actions currently awaiting admin review, ordered oldest-first.
   *
   * <p>Admins should process this queue in order to move issues through their lifecycle.
   */
  @GetMapping("/issue-action/pending")
  @Operation(
      summary = "List pending issue actions",
      description =
          "Returns all REPORT, VERIFY, and RESOLVE actions that are awaiting admin approval,"
              + " ordered by submission time (oldest first).")
  public ResponseEntity<NewAPIResponse<List<IssueActionAdminResponseData>>> getPendingActions() {
    List<IssueActionAdminResponseData> items =
        issueActionAdminService.getPendingActions().stream()
            .map(
                action ->
                    IssueActionAdminResponseData.builder()
                        .actionId(action.getId())
                        .issueId(
                            action.getIssueEntity() != null
                                ? action.getIssueEntity().getId()
                                : null)
                        .submittedByUsername(
                            action.getUserEntity() != null
                                ? action.getUserEntity().getUsername()
                                : null)
                        .action(action.getAction().name())
                        .approvalStatus(action.getApprovalStatus().name())
                        .createdAt(action.getCreatedAt())
                        .build())
            .toList();

    return ResponseEntity.ok(
        NewAPIResponse.<List<IssueActionAdminResponseData>>builder().data(items).build());
  }

  /**
   * Approves a pending issue action and applies the corresponding status transition.
   *
   * @param actionId the ID of the action to approve
   * @param adminUserId the authenticated admin's user ID (injected from the security context)
   */
  @PutMapping("/issue-action/{actionId}/approve")
  @Operation(
      summary = "Approve an issue action",
      description =
          "Approves a pending issue action. Effect depends on the action type: "
              + "REPORT → OPEN, VERIFY → verifyCount++, RESOLVE → RESOLVED.")
  public ResponseEntity<NewAPIResponse<IssueActionResponseData>> approveAction(
      @PathVariable Long actionId, @AuthenticationPrincipal Long adminUserId) {

    Long issueId =
        issueActionAdminService.handleApproval(
            actionId, adminUserId, IssueActionApprovalStatus.APPROVED);

    return ResponseEntity.ok(
        NewAPIResponse.<IssueActionResponseData>builder()
            .data(IssueActionResponseData.builder().issueId(issueId).build())
            .build());
  }

  /**
   * Rejects a pending issue action and reverts or blocks the corresponding status transition.
   *
   * @param actionId the ID of the action to reject
   * @param adminUserId the authenticated admin's user ID (injected from the security context)
   */
  @PutMapping("/issue-action/{actionId}/reject")
  @Operation(
      summary = "Reject an issue action",
      description =
          "Rejects a pending issue action. Effect depends on the action type: "
              + "REPORT → REJECTED, VERIFY → action discarded, RESOLVE → revert to OPEN.")
  public ResponseEntity<NewAPIResponse<IssueActionResponseData>> rejectAction(
      @PathVariable Long actionId, @AuthenticationPrincipal Long adminUserId) {

    Long issueId =
        issueActionAdminService.handleApproval(
            actionId, adminUserId, IssueActionApprovalStatus.REJECTED);

    return ResponseEntity.ok(
        NewAPIResponse.<IssueActionResponseData>builder()
            .data(IssueActionResponseData.builder().issueId(issueId).build())
            .build());
  }
}
