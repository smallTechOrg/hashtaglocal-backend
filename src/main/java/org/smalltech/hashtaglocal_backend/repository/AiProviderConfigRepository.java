package org.smalltech.hashtaglocal_backend.repository;

import java.util.Optional;
import org.smalltech.hashtaglocal_backend.entity.AiProviderConfigEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AiProviderConfigRepository extends JpaRepository<AiProviderConfigEntity, Long> {

  /** The newest row: the config in effect, unless it is a "removed" row (no provider). */
  Optional<AiProviderConfigEntity> findTopByOrderByIdDesc();

  /**
   * Clears the encrypted key on every row except {@code keepId}, so old keys are not kept. A bulk
   * update skips {@code @UpdateTimestamp}, so it moves {@code updatedAt} itself.
   */
  @Modifying
  @Query(
      "UPDATE AiProviderConfigEntity c SET c.apiKeyCiphertext = NULL,"
          + " c.updatedAt = CURRENT_TIMESTAMP"
          + " WHERE c.id <> :keepId AND c.apiKeyCiphertext IS NOT NULL")
  int clearKeysExcept(@Param("keepId") Long keepId);
}
