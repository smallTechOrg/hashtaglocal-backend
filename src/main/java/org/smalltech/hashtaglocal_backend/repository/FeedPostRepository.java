package org.smalltech.hashtaglocal_backend.repository;

import java.time.LocalDateTime;
import java.util.List;
import org.smalltech.hashtaglocal_backend.entity.FeedPostEntity;
import org.smalltech.hashtaglocal_backend.model.FeedPostStatus;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface FeedPostRepository extends JpaRepository<FeedPostEntity, Long> {

  /**
   * First page of the public timeline for a locality, newest first. Ordered by {@code (createdAt,
   * id)} DESC. Caller supplies a {@code Pageable} of size {@code limit}.
   */
  @Query(
      "SELECT p FROM FeedPostEntity p "
          + "WHERE p.locality.id = :localityId "
          + "AND p.status = :status "
          + "AND p.pinned = false "
          + "AND (p.publishedAt IS NULL OR p.publishedAt <= :now) "
          + "ORDER BY p.createdAt DESC, p.id DESC")
  List<FeedPostEntity> findTimelineFirstPage(
      @Param("localityId") Long localityId,
      @Param("status") FeedPostStatus status,
      @Param("now") LocalDateTime now,
      Pageable pageable);

  /**
   * Subsequent timeline page: rows strictly older than the cursor {@code (createdAt, id)}.
   * Tuple-compared so rows sharing a timestamp are never skipped.
   */
  @Query(
      "SELECT p FROM FeedPostEntity p "
          + "WHERE p.locality.id = :localityId "
          + "AND p.status = :status "
          + "AND p.pinned = false "
          + "AND (p.publishedAt IS NULL OR p.publishedAt <= :now) "
          + "AND (p.createdAt < :cursorCreatedAt "
          + "     OR (p.createdAt = :cursorCreatedAt AND p.id < :cursorId)) "
          + "ORDER BY p.createdAt DESC, p.id DESC")
  List<FeedPostEntity> findTimelineAfter(
      @Param("localityId") Long localityId,
      @Param("status") FeedPostStatus status,
      @Param("now") LocalDateTime now,
      @Param("cursorCreatedAt") LocalDateTime cursorCreatedAt,
      @Param("cursorId") Long cursorId,
      Pageable pageable);

  // -------------------------------------------------------------------------
  // Aggregated timeline for a parent (root) locality: the parent's own posts
  // (e.g. #india admin national posts) PLUS every child locality's posts. Each
  // post keeps its own locality, so the client can show a per-post marker.
  // -------------------------------------------------------------------------

  /** First page of the aggregated timeline for {@code rootId} (self + children). */
  @Query(
      "SELECT p FROM FeedPostEntity p "
          + "WHERE (p.locality.id = :rootId OR p.locality.parent.id = :rootId) "
          + "AND p.status = :status "
          + "AND p.pinned = false "
          + "AND (p.publishedAt IS NULL OR p.publishedAt <= :now) "
          + "ORDER BY p.createdAt DESC, p.id DESC")
  List<FeedPostEntity> findAggregatedTimelineFirstPage(
      @Param("rootId") Long rootId,
      @Param("status") FeedPostStatus status,
      @Param("now") LocalDateTime now,
      Pageable pageable);

  /** Subsequent aggregated page: older than the cursor. */
  @Query(
      "SELECT p FROM FeedPostEntity p "
          + "WHERE (p.locality.id = :rootId OR p.locality.parent.id = :rootId) "
          + "AND p.status = :status "
          + "AND p.pinned = false "
          + "AND (p.publishedAt IS NULL OR p.publishedAt <= :now) "
          + "AND (p.createdAt < :cursorCreatedAt "
          + "     OR (p.createdAt = :cursorCreatedAt AND p.id < :cursorId)) "
          + "ORDER BY p.createdAt DESC, p.id DESC")
  List<FeedPostEntity> findAggregatedTimelineAfter(
      @Param("rootId") Long rootId,
      @Param("status") FeedPostStatus status,
      @Param("now") LocalDateTime now,
      @Param("cursorCreatedAt") LocalDateTime cursorCreatedAt,
      @Param("cursorId") Long cursorId,
      Pageable pageable);

  /** Pinned aggregated posts (self + children) — first page only. */
  @Query(
      "SELECT p FROM FeedPostEntity p "
          + "WHERE (p.locality.id = :rootId OR p.locality.parent.id = :rootId) "
          + "AND p.status = :status "
          + "AND p.pinned = true "
          + "AND (p.publishedAt IS NULL OR p.publishedAt <= :now) "
          + "ORDER BY p.createdAt DESC, p.id DESC")
  List<FeedPostEntity> findAggregatedPinned(
      @Param("rootId") Long rootId,
      @Param("status") FeedPostStatus status,
      @Param("now") LocalDateTime now);

  /** Pinned, published posts for a locality (returned on the first page only). */
  @Query(
      "SELECT p FROM FeedPostEntity p "
          + "WHERE p.locality.id = :localityId "
          + "AND p.status = :status "
          + "AND p.pinned = true "
          + "AND (p.publishedAt IS NULL OR p.publishedAt <= :now) "
          + "ORDER BY p.createdAt DESC, p.id DESC")
  List<FeedPostEntity> findPinned(
      @Param("localityId") Long localityId,
      @Param("status") FeedPostStatus status,
      @Param("now") LocalDateTime now);

  /**
   * Admin moderation queue: posts in the given statuses (e.g. AI_BLOCKED, FLAGGED), newest first.
   */
  @Query(
      "SELECT p FROM FeedPostEntity p "
          + "WHERE p.status IN :statuses "
          + "ORDER BY p.createdAt DESC, p.id DESC")
  List<FeedPostEntity> findModerationQueueFirstPage(
      @Param("statuses") List<FeedPostStatus> statuses, Pageable pageable);

  /** Admin moderation queue, subsequent page: older than the cursor. */
  @Query(
      "SELECT p FROM FeedPostEntity p "
          + "WHERE p.status IN :statuses "
          + "AND (p.createdAt < :cursorCreatedAt "
          + "     OR (p.createdAt = :cursorCreatedAt AND p.id < :cursorId)) "
          + "ORDER BY p.createdAt DESC, p.id DESC")
  List<FeedPostEntity> findModerationQueueAfter(
      @Param("statuses") List<FeedPostStatus> statuses,
      @Param("cursorCreatedAt") LocalDateTime cursorCreatedAt,
      @Param("cursorId") Long cursorId,
      Pageable pageable);

  /** Posts stuck awaiting moderation — picked up by the retry sweeper. */
  List<FeedPostEntity> findByStatus(FeedPostStatus status, Pageable pageable);
}
