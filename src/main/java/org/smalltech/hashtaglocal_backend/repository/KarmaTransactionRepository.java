package org.smalltech.hashtaglocal_backend.repository;

import java.time.LocalDateTime;
import java.util.List;
import org.smalltech.hashtaglocal_backend.entity.IssueActionEntity;
import org.smalltech.hashtaglocal_backend.entity.KarmaTransactionEntity;
import org.smalltech.hashtaglocal_backend.model.KarmaTransactionStatus;
import org.smalltech.hashtaglocal_backend.model.KarmaTransactionType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface KarmaTransactionRepository extends JpaRepository<KarmaTransactionEntity, Long> {

  List<KarmaTransactionEntity> findByReferenceActionAndStatus(
      IssueActionEntity referenceAction, KarmaTransactionStatus status);

  boolean existsByUserEntityIdAndTypeAndCreatedAtAfter(
      Long userId, KarmaTransactionType type, LocalDateTime after);
}
