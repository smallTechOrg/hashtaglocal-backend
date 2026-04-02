package org.smalltech.hashtaglocal_backend.service;

import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.smalltech.hashtaglocal_backend.entity.IssueActionEntity;
import org.smalltech.hashtaglocal_backend.entity.UserEntity;
import org.smalltech.hashtaglocal_backend.model.IssueActionApprovalStatus;
import org.smalltech.hashtaglocal_backend.model.IssueActionModel;
import org.smalltech.hashtaglocal_backend.model.IssueStatusModel;
import org.smalltech.hashtaglocal_backend.repository.IssueActionRepository;
import org.smalltech.hashtaglocal_backend.repository.IssueRepository;
import org.smalltech.hashtaglocal_backend.repository.UserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/**
 * Handles admin approval and rejection of issue actions.
 *
 * <p>The following state transitions are driven by admin decisions:
 *
 * <ul>
 *   <li><b>REPORT → APPROVED</b>: issue moves from {@code ONHOLD} to {@code OPEN}.
 *   <li><b>REPORT → REJECTED</b>: issue moves from {@code ONHOLD} to {@code REJECTED}.
 *   <li><b>VERIFY → APPROVED</b>: the action's {@code approvalStatus} is set to {@code APPROVED} —
 *       this increments the issue's {@code verifyCount}.
 *   <li><b>VERIFY → REJECTED</b>: the action is marked {@code REJECTED}; issue status is unchanged.
 *   <li><b>RESOLVE → APPROVED</b>: issue moves from {@code PENDING} to {@code RESOLVED}.
 *   <li><b>RESOLVE → REJECTED</b>: issue reverts from {@code PENDING} back to {@code OPEN}.
 * </ul>
 */
@Service
@RequiredArgsConstructor
@Transactional
public class IssueActionAdminService {

  private final IssueActionRepository issueActionRepository;
  private final IssueRepository issueRepository;
  private final UserRepository userRepository;
  private final KarmaService karmaService;

  /**
   * Approves or rejects a pending issue action.
   *
   * @param actionId the ID of the {@link IssueActionEntity} to review
   * @param adminUserId the authenticated admin's user ID
   * @param decision {@code APPROVED} or {@code REJECTED}
   * @return the ID of the affected issue
   */
  public Long handleApproval(Long actionId, Long adminUserId, IssueActionApprovalStatus decision) {

    if (decision != IssueActionApprovalStatus.APPROVED
        && decision != IssueActionApprovalStatus.REJECTED) {
      throw new ResponseStatusException(
          HttpStatus.BAD_REQUEST, "Decision must be APPROVED or REJECTED");
    }

    IssueActionEntity action =
        issueActionRepository
            .findById(actionId)
            .orElseThrow(
                () -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Action not found"));

    if (action.getApprovalStatus() != IssueActionApprovalStatus.PENDING) {
      throw new ResponseStatusException(
          HttpStatus.BAD_REQUEST,
          "Action is not pending approval (current status: " + action.getApprovalStatus() + ")");
    }

    UserEntity admin =
        userRepository
            .findById(adminUserId)
            .orElseThrow(
                () ->
                    new ResponseStatusException(
                        HttpStatus.INTERNAL_SERVER_ERROR, "Admin user not found"));

    var issueEntity = action.getIssueEntity();
    IssueActionModel actionType = action.getAction();

    // --- Apply state transitions ---
    if (actionType == IssueActionModel.REPORT) {
      if (decision == IssueActionApprovalStatus.APPROVED) {
        issueEntity.setStatus(IssueStatusModel.OPEN);
      } else {
        issueEntity.setStatus(IssueStatusModel.REJECTED);
        // Cascade-reject all other PENDING actions on this issue so they don't
        // linger in the pending queue after the issue itself is rejected.
        issueActionRepository
            .findByIssueEntityAndApprovalStatus(issueEntity, IssueActionApprovalStatus.PENDING)
            .stream()
            .filter(a -> !a.getId().equals(action.getId()))
            .forEach(
                a -> {
                  a.setApprovalStatus(IssueActionApprovalStatus.REJECTED);
                  a.setApprovedByUser(admin);
                  a.setApprovedAt(LocalDateTime.now());
                  issueActionRepository.save(a);
                  karmaService.revokeKarma(a);
                });
      }
      issueEntity.setUpdatedAt(LocalDateTime.now());
      issueRepository.save(issueEntity);

    } else if (actionType == IssueActionModel.VERIFY) {
      // VERIFY approval/rejection only affects the action record.
      // Issue status is not changed — verifyCount is derived from APPROVED VERIFY actions.

    } else if (actionType == IssueActionModel.RESOLVE) {
      if (decision == IssueActionApprovalStatus.APPROVED) {
        issueEntity.setStatus(IssueStatusModel.RESOLVED);
      } else {
        // Revert PENDING back to OPEN so the issue remains active
        issueEntity.setStatus(IssueStatusModel.OPEN);
      }
      issueEntity.setUpdatedAt(LocalDateTime.now());
      issueRepository.save(issueEntity);

    } else {
      throw new ResponseStatusException(
          HttpStatus.BAD_REQUEST, "Action type " + actionType + " does not support admin approval");
    }

    // Stamp the action with admin identity and result
    action.setApprovalStatus(decision);
    action.setApprovedByUser(admin);
    action.setApprovedAt(LocalDateTime.now());
    issueActionRepository.save(action);

    // --- Karma: confirm or revoke based on decision ---
    if (decision == IssueActionApprovalStatus.APPROVED) {
      karmaService.confirmKarma(action);

      // If a VERIFY action was approved, award REPORTED_ISSUE_VERIFIED karma to the issue reporter
      if (actionType == IssueActionModel.VERIFY) {
        UserEntity issueReporter = issueEntity.getUserEntity();
        if (issueReporter != null
            && !issueReporter.getId().equals(action.getUserEntity().getId())) {
          karmaService.awardReportedIssueVerifiedKarma(issueReporter, action);
        }
      }
    } else {
      karmaService.revokeKarma(action);
    }

    return issueEntity.getId();
  }

  /**
   * Returns all issue actions currently awaiting admin review, ordered oldest-first so admins
   * process them in submission order.
   */
  @Transactional(readOnly = true)
  public List<IssueActionEntity> getPendingActions() {
    return issueActionRepository.findByApprovalStatusOrderByCreatedAtAsc(
        IssueActionApprovalStatus.PENDING);
  }

  /** Returns all recently reviewed (APPROVED or REJECTED) actions, ordered newest-first. */
  @Transactional(readOnly = true)
  public List<IssueActionEntity> getRecentlyReviewedActions() {
    return issueActionRepository.findByApprovalStatusInOrderByApprovedAtDesc(
        List.of(IssueActionApprovalStatus.APPROVED, IssueActionApprovalStatus.REJECTED));
  }
}
