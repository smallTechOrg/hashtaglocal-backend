package org.smalltech.hashtaglocal_backend.repository;

import java.util.List;
import org.smalltech.hashtaglocal_backend.entity.IssueActionEntity;
import org.smalltech.hashtaglocal_backend.entity.IssueEntity;
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
}
