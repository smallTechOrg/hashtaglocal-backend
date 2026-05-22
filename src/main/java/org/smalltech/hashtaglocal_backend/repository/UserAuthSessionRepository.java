package org.smalltech.hashtaglocal_backend.repository;

import java.util.Optional;
import org.smalltech.hashtaglocal_backend.entity.UserAuthSessionEntity;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface UserAuthSessionRepository extends JpaRepository<UserAuthSessionEntity, Long> {

  /** Fetches the session along with the user (including role) in a single query. */
  @EntityGraph(attributePaths = {"user"})
  Optional<UserAuthSessionEntity> findByAccessToken(String accessToken);

  Optional<UserAuthSessionEntity> findByRefreshToken(String refreshToken);

  /** Revokes every active login session after the user initiates account deletion. */
  @Modifying
  @Query("update UserAuthSessionEntity s set s.isActive = false where s.user.id = :userId")
  int deactivateAllByUserId(@Param("userId") Long userId);
}
