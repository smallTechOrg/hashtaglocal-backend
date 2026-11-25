package org.smalltech.hashtaglocal_backend.repository;

import java.time.LocalDateTime;
import java.util.List;
import org.smalltech.hashtaglocal_backend.entity.AppVersionSeenEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface AppVersionSeenRepository extends JpaRepository<AppVersionSeenEntity, Long> {

  /**
   * Records the version a session reported in a week, keeping the last one seen. A single
   * statement, so two refreshes of the same session at once cannot both insert.
   */
  @Modifying
  @Query(
      value =
          "INSERT INTO app_version_seen"
              + " (session_id, user_id, platform, app_version, week_year, week_number, last_seen_at)"
              + " VALUES (:sessionId, :userId, :platform, :appVersion, :weekYear, :weekNumber,"
              + " :seenAt)"
              + " ON CONFLICT (session_id, week_year, week_number) DO UPDATE"
              + " SET app_version = EXCLUDED.app_version, platform = EXCLUDED.platform,"
              + " last_seen_at = EXCLUDED.last_seen_at",
      nativeQuery = true)
  void upsert(
      @Param("sessionId") Long sessionId,
      @Param("userId") Long userId,
      @Param("platform") String platform,
      @Param("appVersion") String appVersion,
      @Param("weekYear") int weekYear,
      @Param("weekNumber") int weekNumber,
      @Param("seenAt") LocalDateTime seenAt);

  List<AppVersionSeenEntity> findByWeekYearAndWeekNumber(int weekYear, int weekNumber);

  /** Every version reported in this week or any earlier one. */
  @Query(
      "SELECT DISTINCT s.appVersion FROM AppVersionSeenEntity s"
          + " WHERE s.weekYear < :weekYear"
          + " OR (s.weekYear = :weekYear AND s.weekNumber <= :weekNumber)")
  List<String> findVersionsSeenUpTo(
      @Param("weekYear") int weekYear, @Param("weekNumber") int weekNumber);
}
