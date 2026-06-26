package org.smalltech.hashtaglocal_backend.repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.smalltech.hashtaglocal_backend.entity.IssueEntity;
import org.smalltech.hashtaglocal_backend.model.IssueStatusModel;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface IssueRepository extends JpaRepository<IssueEntity, Long> {
  @EntityGraph(attributePaths = {"userEntity", "location", "location.locality"})
  Optional<IssueEntity> findById(Long id);

  /**
   * Publicly-visible issues ({@code OPEN} or {@code RESOLVED}) that have a resolved locality but no
   * ISSUE_REF feed post yet — the backlog for the feed backfill. ONHOLD/REJECTED/PENDING issues are
   * excluded so the feed never shows unapproved issues. Oldest first so backfilled posts keep
   * chronological order. Idempotent: rerunning skips issues already referenced.
   */
  @EntityGraph(attributePaths = {"userEntity", "location", "location.locality"})
  @Query(
      "SELECT i FROM IssueEntity i "
          + "WHERE i.location IS NOT NULL AND i.location.locality IS NOT NULL "
          // Only publicly-visible issues belong in the feed — never ONHOLD/REJECTED/PENDING.
          + "AND i.status IN ("
          + "  org.smalltech.hashtaglocal_backend.model.IssueStatusModel.OPEN, "
          + "  org.smalltech.hashtaglocal_backend.model.IssueStatusModel.RESOLVED) "
          + "AND NOT EXISTS ("
          + "  SELECT 1 FROM FeedPostContentEntity c "
          + "  WHERE c.issue = i AND c.post.kind = org.smalltech.hashtaglocal_backend.model.FeedPostKind.ISSUE_REF"
          + ") "
          + "ORDER BY i.createdAt ASC")
  List<IssueEntity> findIssuesWithoutFeedRef(org.springframework.data.domain.Pageable pageable);

  @EntityGraph(attributePaths = {"userEntity", "location", "location.locality"})
  List<IssueEntity> findAll();

  @EntityGraph(attributePaths = {"userEntity", "location", "location.locality"})
  List<IssueEntity> findByStatus(IssueStatusModel status);

  @EntityGraph(attributePaths = {"userEntity", "location", "location.locality"})
  List<IssueEntity> findByStatusOrderByCreatedAtDesc(IssueStatusModel status);

  @EntityGraph(attributePaths = {"userEntity", "location", "location.locality"})
  List<IssueEntity> findByStatusInOrderByCreatedAtDesc(List<IssueStatusModel> statuses);

  @EntityGraph(attributePaths = {"userEntity", "location", "location.locality"})
  @Query(
      "SELECT i FROM IssueEntity i WHERE i.status IN :statuses AND i.createdAt >= :startDate ORDER BY i.createdAt DESC")
  List<IssueEntity> findByStatusInAndCreatedAtAfterOrderByCreatedAtDesc(
      @Param("statuses") List<IssueStatusModel> statuses,
      @Param("startDate") LocalDateTime startDate);

  @EntityGraph(attributePaths = {"userEntity", "location", "location.locality"})
  @Query(
      "SELECT i FROM IssueEntity i WHERE i.status IN :statuses AND i.createdAt >= :startDate AND LOWER(i.location.locality.hashtag) = LOWER(:localityHashtag) ORDER BY i.createdAt DESC")
  List<IssueEntity> findByStatusInAndCreatedAtAfterAndLocalityHashtagOrderByCreatedAtDesc(
      @Param("statuses") List<IssueStatusModel> statuses,
      @Param("startDate") LocalDateTime startDate,
      @Param("localityHashtag") String localityHashtag);

  @Query(
      value =
          "SELECT i.* FROM issues i "
              + "INNER JOIN locations l ON i.location_id = l.id "
              + "WHERE i.status IN :statuses "
              + "AND i.created_at >= :startDate "
              + "AND ST_DWithin(l.point::geography, ST_SetSRID(ST_MakePoint(:lng, :lat), 4326)::geography, :radiusMeters) "
              + "ORDER BY i.created_at DESC",
      nativeQuery = true)
  List<IssueEntity> findByStatusInAndCreatedAtAfterAndWithinRadius(
      @Param("statuses") List<String> statuses,
      @Param("startDate") LocalDateTime startDate,
      @Param("lat") double lat,
      @Param("lng") double lng,
      @Param("radiusMeters") double radiusMeters);

  @EntityGraph(attributePaths = {"userEntity", "location", "location.locality"})
  @Query(
      "SELECT i FROM IssueEntity i WHERE (i.status IN :statuses OR (i.status = org.smalltech.hashtaglocal_backend.model.IssueStatusModel.ONHOLD AND i.userEntity.id = :ownerUserId)) AND i.createdAt >= :startDate ORDER BY i.createdAt DESC")
  List<IssueEntity> findByStatusInOrOnholdOwnedAndCreatedAtAfterOrderByCreatedAtDesc(
      @Param("statuses") List<IssueStatusModel> statuses,
      @Param("ownerUserId") Long ownerUserId,
      @Param("startDate") LocalDateTime startDate);

  @EntityGraph(attributePaths = {"userEntity", "location", "location.locality"})
  @Query(
      "SELECT i FROM IssueEntity i WHERE (i.status IN :statuses OR (i.status = org.smalltech.hashtaglocal_backend.model.IssueStatusModel.ONHOLD AND i.userEntity.id = :ownerUserId)) AND i.createdAt >= :startDate AND LOWER(i.location.locality.hashtag) = LOWER(:localityHashtag) ORDER BY i.createdAt DESC")
  List<IssueEntity>
      findByStatusInOrOnholdOwnedAndCreatedAtAfterAndLocalityHashtagOrderByCreatedAtDesc(
          @Param("statuses") List<IssueStatusModel> statuses,
          @Param("ownerUserId") Long ownerUserId,
          @Param("startDate") LocalDateTime startDate,
          @Param("localityHashtag") String localityHashtag);

  @Query(
      value =
          "SELECT i.* FROM issues i "
              + "INNER JOIN locations l ON i.location_id = l.id "
              + "WHERE (i.status IN :statuses OR (i.status = 'ONHOLD' AND i.user_id = :ownerUserId)) "
              + "AND i.created_at >= :startDate "
              + "AND ST_DWithin(l.point::geography, ST_SetSRID(ST_MakePoint(:lng, :lat), 4326)::geography, :radiusMeters) "
              + "ORDER BY i.created_at DESC",
      nativeQuery = true)
  List<IssueEntity> findByStatusInOrOnholdOwnedAndCreatedAtAfterAndWithinRadius(
      @Param("statuses") List<String> statuses,
      @Param("ownerUserId") Long ownerUserId,
      @Param("startDate") LocalDateTime startDate,
      @Param("lat") double lat,
      @Param("lng") double lng,
      @Param("radiusMeters") double radiusMeters);

  @Query(
      "SELECT COUNT(i) FROM IssueEntity i WHERE i.userEntity.id = :userId AND i.status <> org.smalltech.hashtaglocal_backend.model.IssueStatusModel.REJECTED")
  long countByUserExcludingRejected(@Param("userId") Long userId);

  @Query(
      "SELECT COUNT(i) FROM IssueEntity i WHERE i.userEntity.id = :userId AND i.status = :status")
  long countByUserAndStatus(@Param("userId") Long userId, @Param("status") IssueStatusModel status);

  @Query(
      "SELECT COUNT(i) FROM IssueEntity i WHERE i.userEntity.id = :userId AND i.status IN :statuses")
  long countByUserAndStatusIn(
      @Param("userId") Long userId, @Param("statuses") List<IssueStatusModel> statuses);

  // ---- Metrics ----

  @Query(
      "SELECT COUNT(i) FROM IssueEntity i WHERE i.createdAt BETWEEN :start AND :end"
          + " AND i.status <> org.smalltech.hashtaglocal_backend.model.IssueStatusModel.REJECTED")
  long countReportedBetween(@Param("start") LocalDateTime start, @Param("end") LocalDateTime end);

  @EntityGraph(attributePaths = {"userEntity", "location", "location.locality"})
  @Query(
      "SELECT i FROM IssueEntity i WHERE i.createdAt BETWEEN :start AND :end"
          + " AND i.status <> org.smalltech.hashtaglocal_backend.model.IssueStatusModel.REJECTED"
          + " ORDER BY i.createdAt DESC")
  List<IssueEntity> findReportedBetween(
      @Param("start") LocalDateTime start, @Param("end") LocalDateTime end);
}
