package org.smalltech.hashtaglocal_backend.repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.smalltech.hashtaglocal_backend.entity.GovPortalEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface GovPortalRepository extends JpaRepository<GovPortalEntity, Long> {

  List<GovPortalEntity> findByIssueEntityId(Long issueId);

  @Query("SELECT g FROM GovPortalEntity g WHERE g.issueEntity.id IN :issueIds")
  List<GovPortalEntity> findByIssueIds(@Param("issueIds") Collection<Long> issueIds);

  Optional<GovPortalEntity> findFirstByStatusOrderByUpdatedAtAsc(String status);

  Optional<GovPortalEntity> findFirstByStatusAndIdNotInOrderByUpdatedAtAsc(
      String status, Collection<Long> excludedIds);
}
