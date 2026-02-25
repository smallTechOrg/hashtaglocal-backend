package org.smalltech.hashtaglocal_backend.repository;

import java.util.Optional;
import org.smalltech.hashtaglocal_backend.entity.UserAuthProviderEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface UserAuthProviderRepository extends JpaRepository<UserAuthProviderEntity, Long> {
  Optional<UserAuthProviderEntity> findByProviderTypeAndProviderUserId(
      String providerType, String providerUserId);
}
