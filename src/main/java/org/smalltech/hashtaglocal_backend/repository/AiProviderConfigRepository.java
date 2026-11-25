package org.smalltech.hashtaglocal_backend.repository;

import java.util.Optional;
import org.smalltech.hashtaglocal_backend.entity.AiProviderConfigEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AiProviderConfigRepository extends JpaRepository<AiProviderConfigEntity, Long> {

  /** The connection in effect: the active row, if any. */
  Optional<AiProviderConfigEntity> findFirstByIsActiveTrueOrderByIdDesc();

  /**
   * Sets the active row inactive and clears its encrypted key, recording who did it. A bulk update
   * skips {@code @UpdateTimestamp}, so it moves {@code updatedAt} itself.
   */
  @Modifying
  @Query(
      "UPDATE AiProviderConfigEntity c SET c.isActive = false, c.apiKeyCiphertext = NULL,"
          + " c.updatedBy = :by, c.updatedAt = CURRENT_TIMESTAMP"
          + " WHERE c.isActive = true")
  int deactivateAll(@Param("by") Long by);
}
