package org.smalltech.hashtaglocal_backend.repository;

import java.time.LocalDateTime;
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

  /** Returns distinct non-null notification tokens across every active session — for broadcasts. */
  @Query(
      "SELECT DISTINCT s.notificationToken FROM UserAuthSessionEntity s "
          + "WHERE s.isActive = true AND s.notificationToken IS NOT NULL")
  List<String> findAllActiveNotificationTokens();

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

  // ---- Session cap ----

  /** Returns active session IDs for a user, oldest first — used to enforce the per-user cap. */
  @Query(
      "SELECT s.id FROM UserAuthSessionEntity s "
          + "WHERE s.user.id = :userId AND s.isActive = true "
          + "ORDER BY s.createdAt ASC")
  List<Long> findActiveSessionIdsByUserIdOrderByCreatedAsc(@Param("userId") Long userId);

  /** Bulk-deactivates sessions by ID. Used to evict the oldest sessions when the cap is hit. */
  @Modifying
  @Query("UPDATE UserAuthSessionEntity s SET s.isActive = false WHERE s.id IN :ids")
  int deactivateByIds(@Param("ids") List<Long> ids);

  // ---- Cleanup ----

  /**
   * Deletes sessions whose refresh token expired before the given epoch-second cutoff. Safe to
   * delete: an expired refresh token can never be used to authenticate again.
   */
  @Modifying
  @Query(
      "DELETE FROM UserAuthSessionEntity s "
          + "WHERE s.refreshTokenExpiryTs IS NOT NULL AND s.refreshTokenExpiryTs < :cutoffEpochSeconds")
  int deleteByRefreshTokenExpiredBefore(@Param("cutoffEpochSeconds") long cutoffEpochSeconds);

  /**
   * Deletes inactive sessions that have not been touched in a long time. Catches rows deactivated
   * by account deletion or manual revocation.
   */
  @Modifying
  @Query(
      "DELETE FROM UserAuthSessionEntity s " + "WHERE s.isActive = false AND s.updatedAt < :cutoff")
  int deleteInactiveSessionsOlderThan(@Param("cutoff") LocalDateTime cutoff);
}
