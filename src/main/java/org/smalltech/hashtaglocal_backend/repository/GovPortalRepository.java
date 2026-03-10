package org.smalltech.hashtaglocal_backend.repository;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.smalltech.hashtaglocal_backend.entity.GovPortalEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface GovPortalRepository extends JpaRepository<GovPortalEntity, Long> {

  List<GovPortalEntity> findByIssueEntityId(Long issueId);

  Optional<GovPortalEntity> findFirstByUpdatedAtBeforeOrderByUpdatedAtAsc(LocalDateTime cutoff);

  Optional<GovPortalEntity> findFirstByUpdatedAtBeforeAndIdNotInOrderByUpdatedAtAsc(
      LocalDateTime cutoff, Collection<Long> excludedIds);
}
