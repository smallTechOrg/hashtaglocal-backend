package org.smalltech.hashtaglocal_backend.repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.smalltech.hashtaglocal_backend.entity.BulletinEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface BulletinRepository extends JpaRepository<BulletinEntity, Long> {

  Optional<BulletinEntity> findByLocalityIdAndDate(Long localityId, LocalDate date);

  List<BulletinEntity> findByDateOrderByLocalityId(LocalDate date);

  List<BulletinEntity> findByLocalityIdOrderByDateDesc(Long localityId);

  Optional<BulletinEntity> findByQuizId(Long quizId);

  /** Returns the most recent quiz questions already asked for a locality (most recent first). */
  @Query(
      value =
          "SELECT q.question FROM bulletins b "
              + "JOIN quizzes q ON q.id = b.quiz_id "
              + "WHERE b.locality_id = :localityId AND b.quiz_id IS NOT NULL "
              + "ORDER BY b.date DESC "
              + "LIMIT :limit",
      nativeQuery = true)
  List<String> findRecentQuizQuestions(
      @Param("localityId") Long localityId, @Param("limit") int limit);

  /** Returns [locality_id, date] pairs that have a quiz in the given date range. */
  @Query(
      value =
          "SELECT b.locality_id, b.date::text FROM bulletins b "
              + "WHERE b.date BETWEEN :from AND :to AND b.quiz_id IS NOT NULL",
      nativeQuery = true)
  List<Object[]> findCoveredLocalityDatePairs(
      @Param("from") LocalDate from, @Param("to") LocalDate to);
}
