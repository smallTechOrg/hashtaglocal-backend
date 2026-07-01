package org.smalltech.hashtaglocal_backend.repository;

import java.time.LocalDateTime;
import org.smalltech.hashtaglocal_backend.entity.NotificationLogEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface NotificationLogRepository extends JpaRepository<NotificationLogEntity, Long> {

  @Query(
      "SELECT COALESCE(SUM(n.recipientCount), 0) FROM NotificationLogEntity n"
          + " WHERE n.createdAt BETWEEN :start AND :end")
  long sumRecipientCountBetween(
      @Param("start") LocalDateTime start, @Param("end") LocalDateTime end);
}
