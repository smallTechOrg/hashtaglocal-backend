package org.smalltech.hashtaglocal_backend.repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.smalltech.hashtaglocal_backend.entity.Locality;
import org.smalltech.hashtaglocal_backend.entity.UserEntity;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface UserRepository extends JpaRepository<UserEntity, Long> {
  Optional<UserEntity> findByUsername(String username);

  /**
   * The distinct localities that saved users belong to ({@code users.location_id} → {@code
   * locations.locality_id}). The bulletin jobs run over exactly this set, so a locality is picked
   * up automatically the day after its first user appears.
   */
  @Query(
      "SELECT DISTINCT loc.locality FROM UserEntity u JOIN u.location loc "
          + "WHERE loc.locality IS NOT NULL")
  List<Locality> findDistinctUserLocalities();

  // ---- Metrics ----

  @Query("SELECT COUNT(u) FROM UserEntity u WHERE u.createdAt BETWEEN :start AND :end")
  long countNewUsersBetween(@Param("start") LocalDateTime start, @Param("end") LocalDateTime end);

  @Query(
      "SELECT COUNT(DISTINCT i.userEntity.id) FROM IssueEntity i"
          + " WHERE i.createdAt BETWEEN :start AND :end")
  long countActiveUsersBetween(
      @Param("start") LocalDateTime start, @Param("end") LocalDateTime end);

  @EntityGraph(attributePaths = {"location", "location.locality"})
  @Query(
      "SELECT u FROM UserEntity u WHERE u.createdAt BETWEEN :start AND :end"
          + " ORDER BY u.createdAt DESC")
  List<UserEntity> findNewUsersBetween(
      @Param("start") LocalDateTime start, @Param("end") LocalDateTime end);

  @EntityGraph(attributePaths = {"location", "location.locality"})
  @Query(
      "SELECT DISTINCT i.userEntity FROM IssueEntity i WHERE i.createdAt BETWEEN :start AND :end")
  List<UserEntity> findActiveUsersBetween(
      @Param("start") LocalDateTime start, @Param("end") LocalDateTime end);
}
