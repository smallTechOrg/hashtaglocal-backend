package org.smalltech.hashtaglocal_backend.repository;

import java.time.LocalDateTime;
import org.smalltech.hashtaglocal_backend.entity.NotificationLogEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface NotificationLogRepository extends JpaRepository<NotificationLogEntity, Long> {

  /** Used by the weekly ops digest. */
  long countByCreatedAtBetween(LocalDateTime start, LocalDateTime end);
}
