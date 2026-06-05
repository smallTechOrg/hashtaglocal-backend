package org.smalltech.hashtaglocal_backend.repository;

import java.util.List;
import java.util.Optional;
import org.smalltech.hashtaglocal_backend.entity.UserAuthSessionEntity;
import org.smalltech.hashtaglocal_backend.model.Platform;
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

  /** Returns non-null notification tokens for all active sessions belonging to this user. */
  @Query(
      "SELECT s.notificationToken FROM UserAuthSessionEntity s "
          + "WHERE s.user.id = :userId AND s.isActive = true AND s.notificationToken IS NOT NULL")
  List<String> findActiveNotificationTokensByUserId(@Param("userId") Long userId);

  /** Nulls out a stale notification token reported back by FCM. */
  @Modifying
  @Query(
      "UPDATE UserAuthSessionEntity s SET s.notificationToken = NULL "
          + "WHERE s.notificationToken = :token")
  int clearNotificationToken(@Param("token") String token);

  /** Clears notification token on all sessions for a user+platform (sessions remain active). */
  @Modifying
  @Query(
      "UPDATE UserAuthSessionEntity s SET s.notificationToken = NULL "
          + "WHERE s.user.id = :userId AND s.platform = :platform")
  int clearNotificationTokenByUserIdAndPlatform(
      @Param("userId") Long userId, @Param("platform") Platform platform);

  /**
   * Deactivates active sessions for this user+platform that already hold a notification token,
   * preventing duplicate FCM delivery to the same platform.
   */
  @Modifying
  @Query(
      "UPDATE UserAuthSessionEntity s SET s.isActive = false "
          + "WHERE s.user.id = :userId AND s.platform = :platform "
          + "AND s.notificationToken IS NOT NULL AND s.isActive = true")
  int deactivateByUserIdAndPlatformWithToken(
      @Param("userId") Long userId, @Param("platform") Platform platform);
}
