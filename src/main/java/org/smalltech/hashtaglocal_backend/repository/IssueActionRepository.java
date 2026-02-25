package org.smalltech.hashtaglocal_backend.repository;

import org.smalltech.hashtaglocal_backend.entity.IssueActionEntity;
import org.smalltech.hashtaglocal_backend.entity.IssueEntity;
import org.smalltech.hashtaglocal_backend.model.IssueActionModel;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface IssueActionRepository extends JpaRepository<IssueActionEntity, Long> {

  @Query(
      "SELECT COUNT(DISTINCT ia.userEntity.id) "
          + "FROM IssueActionEntity ia "
          + "WHERE ia.issueEntity = :issue AND ia.action = :action")
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
}
