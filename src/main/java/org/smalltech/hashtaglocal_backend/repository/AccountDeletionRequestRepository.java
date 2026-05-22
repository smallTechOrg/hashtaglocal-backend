package org.smalltech.hashtaglocal_backend.repository;

import java.util.Optional;
import org.smalltech.hashtaglocal_backend.entity.AccountDeletionRequestEntity;
import org.smalltech.hashtaglocal_backend.model.AccountDeletionRequestStatus;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * Created as the data access layer for account deletion requests. Supports idempotent
 * request submission (find-by-user-and-status) and admin lookup of pending requests.
 */
@Repository
public interface AccountDeletionRequestRepository
    extends JpaRepository<AccountDeletionRequestEntity, Long> {

  /**
   * Used to make account deletion requests idempotent and to block login during pending deletion.
   */
  @EntityGraph(attributePaths = {"user"})
  Optional<AccountDeletionRequestEntity> findByUserIdAndStatus(
      Long userId, AccountDeletionRequestStatus status);
}
