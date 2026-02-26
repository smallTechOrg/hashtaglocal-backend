package org.smalltech.hashtaglocal_backend.repository;

import java.util.Optional;
import org.smalltech.hashtaglocal_backend.entity.UserAuthSessionEntity;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface UserAuthSessionRepository extends JpaRepository<UserAuthSessionEntity, Long> {

  /** Fetches the session along with the user (including role) in a single query. */
  @EntityGraph(attributePaths = {"user"})
  Optional<UserAuthSessionEntity> findByAccessToken(String accessToken);

  Optional<UserAuthSessionEntity> findByRefreshToken(String refreshToken);
}
