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

  @Query(
      "SELECT COUNT(i) FROM IssueEntity i WHERE i.userEntity.id = :userId AND i.status <> org.smalltech.hashtaglocal_backend.model.IssueStatusModel.REJECTED")
  long countByUserExcludingRejected(@Param("userId") Long userId);

  @Query(
      "SELECT COUNT(i) FROM IssueEntity i WHERE i.userEntity.id = :userId AND i.status = :status")
  long countByUserAndStatus(@Param("userId") Long userId, @Param("status") IssueStatusModel status);
}
