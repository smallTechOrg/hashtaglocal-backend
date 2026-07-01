package org.smalltech.hashtaglocal_backend.repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.smalltech.hashtaglocal_backend.entity.QuizAttemptEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface QuizAttemptRepository extends JpaRepository<QuizAttemptEntity, Long> {

  Optional<QuizAttemptEntity> findByUserIdAndQuizId(Long userId, Long quizId);

  boolean existsByQuizId(Long quizId);

  // ---- Metrics ----

  @Query("SELECT COUNT(qa) FROM QuizAttemptEntity qa WHERE qa.createdAt BETWEEN :start AND :end")
  long countAttemptsBetween(@Param("start") LocalDateTime start, @Param("end") LocalDateTime end);

  @Query(
      "SELECT COUNT(DISTINCT qa.userId) FROM QuizAttemptEntity qa"
          + " WHERE qa.createdAt BETWEEN :start AND :end")
  long countDistinctUsersBetween(
      @Param("start") LocalDateTime start, @Param("end") LocalDateTime end);

  @Query(
      "SELECT DISTINCT qa.userId FROM QuizAttemptEntity qa"
          + " WHERE qa.createdAt BETWEEN :start AND :end")
  List<Long> findDistinctUserIdsBetween(
      @Param("start") LocalDateTime start, @Param("end") LocalDateTime end);
}
