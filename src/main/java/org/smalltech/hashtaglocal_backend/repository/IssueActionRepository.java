package org.smalltech.hashtaglocal_backend.repository;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.smalltech.hashtaglocal_backend.entity.IssueActionEntity;
import org.smalltech.hashtaglocal_backend.entity.IssueEntity;
import org.smalltech.hashtaglocal_backend.entity.UserEntity;
import org.smalltech.hashtaglocal_backend.model.IssueActionApprovalStatus;
import org.smalltech.hashtaglocal_backend.model.IssueActionModel;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface IssueActionRepository extends JpaRepository<IssueActionEntity, Long> {

  /**
   * Returns actions that carry media for a given issue, filtered by the action's approval status.
   * Used by the view layer to build the media list for an issue response.
   *
   * <ul>
   *   <li>Public viewers: pass {@code [APPROVED, NOT_REQUIRED]}
   *   <li>Issue owner: pass {@code [APPROVED, NOT_REQUIRED, PENDING]}
   * </ul>
   */
  @Query(
      "SELECT a FROM IssueActionEntity a "
          + "WHERE a.issueEntity = :issue "
          + "AND a.media IS NOT NULL "
          + "AND a.approvalStatus IN :statuses")
  List<IssueActionEntity> findByIssueWithMediaAndApprovalStatusIn(
      @Param("issue") IssueEntity issue,
      @Param("statuses") List<IssueActionApprovalStatus> statuses);

  /**
   * Counts distinct users who have had their VERIFY (or other) action admin-approved on an issue.
   * Only {@code APPROVED} actions contribute so that pending or rejected verifications are not
   * counted.
   */
  @Query(
      "SELECT COUNT(DISTINCT ia.userEntity.id) "
          + "FROM IssueActionEntity ia "
          + "WHERE ia.issueEntity = :issue "
          + "AND ia.action = :action "
          + "AND ia.approvalStatus = org.smalltech.hashtaglocal_backend.model.IssueActionApprovalStatus.APPROVED")
  int countDistinctUserByIssueAndAction(
      @Param("issue") IssueEntity issueEntity, @Param("action") IssueActionModel action);

  @Query(
      "SELECT COUNT(DISTINCT ia.issueEntity.id) "
          + "FROM IssueActionEntity ia "
          + "WHERE ia.userEntity.id = :userId "
          + "AND ia.action = :action "
          + "AND ia.issueEntity.userEntity.id <> :userId")
  long countDistinctIssuesByUserAndActionExcludingOwnIssues(
      @Param("userId") Long userId, @Param("action") IssueActionModel action);

  /**
   * Returns all actions with a given approval status, ordered by creation time (oldest first). Used
   * by the admin queue endpoint to surface all pending approvals.
   */
  List<IssueActionEntity> findByApprovalStatusOrderByCreatedAtAsc(
      IssueActionApprovalStatus approvalStatus);

  /**
   * Returns all actions for a given issue with the specified approval status. Used to cascade
   * status changes (e.g. reject all remaining PENDING actions when the REPORT is rejected).
   */
  List<IssueActionEntity> findByIssueEntityAndApprovalStatus(
      IssueEntity issueEntity, IssueActionApprovalStatus approvalStatus);

  /**
   * Returns recently reviewed (APPROVED or REJECTED) actions, ordered newest-first. Used by the
   * admin history endpoint.
   */
  List<IssueActionEntity> findByApprovalStatusInOrderByApprovedAtDesc(
      List<IssueActionApprovalStatus> statuses);

  List<IssueActionEntity> findByIssueEntityAndActionAndApprovalStatus(
      IssueEntity issueEntity, IssueActionModel action, IssueActionApprovalStatus approvalStatus);

  /** Returns the user who filed the REPORT action for the given issue. */
  @Query(
      "SELECT a.userEntity FROM IssueActionEntity a "
          + "WHERE a.issueEntity.id = :issueId AND a.action = 'REPORT'")
  Optional<UserEntity> findReporterByIssueId(@Param("issueId") Long issueId);

  /** Bulk: returns the subset of issueIds that have at least one approved VERIFY action. */
  @Query(
      "SELECT DISTINCT ia.issueEntity.id FROM IssueActionEntity ia "
          + "WHERE ia.issueEntity.id IN :issueIds "
          + "AND ia.action = org.smalltech.hashtaglocal_backend.model.IssueActionModel.VERIFY "
          + "AND ia.approvalStatus = org.smalltech.hashtaglocal_backend.model.IssueActionApprovalStatus.APPROVED")
  Set<Long> findVerifiedIssueIds(@Param("issueIds") Collection<Long> issueIds);

  // ---- Metrics ----

  @Query(
      "SELECT COUNT(ia) FROM IssueActionEntity ia WHERE ia.approvalStatus = org.smalltech.hashtaglocal_backend.model.IssueActionApprovalStatus.PENDING")
  long countPendingActions();

  @Query(
      "SELECT COUNT(DISTINCT ia.issueEntity.id) FROM IssueActionEntity ia"
          + " WHERE ia.action = org.smalltech.hashtaglocal_backend.model.IssueActionModel.VERIFY"
          + " AND ia.approvalStatus = org.smalltech.hashtaglocal_backend.model.IssueActionApprovalStatus.APPROVED"
          + " AND ia.approvedAt BETWEEN :start AND :end")
  long countVerifiedIssuesBetween(
      @Param("start") LocalDateTime start, @Param("end") LocalDateTime end);

  @Query(
      "SELECT DISTINCT ia FROM IssueActionEntity ia"
          + " WHERE ia.action = org.smalltech.hashtaglocal_backend.model.IssueActionModel.VERIFY"
          + " AND ia.approvalStatus = org.smalltech.hashtaglocal_backend.model.IssueActionApprovalStatus.APPROVED"
          + " AND ia.approvedAt BETWEEN :start AND :end"
          + " ORDER BY ia.approvedAt DESC")
  List<IssueActionEntity> findVerifiedActionsBetween(
      @Param("start") LocalDateTime start, @Param("end") LocalDateTime end);
}
