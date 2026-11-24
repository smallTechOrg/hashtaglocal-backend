package org.smalltech.hashtaglocal_backend.repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.smalltech.hashtaglocal_backend.entity.QuizEntity;
import org.smalltech.hashtaglocal_backend.model.QuizStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface QuizRepository extends JpaRepository<QuizEntity, Long> {

  /** Quizzes for a locality created since {@code since}; used for the no-repeat window. */
  List<QuizEntity> findByLocality_IdAndCreatedAtAfter(Long localityId, LocalDateTime since);

  /** Quizzes in a review state, oldest first within each hashtag. */
  List<QuizEntity> findByStatusOrderByLocality_HashtagAscCreatedAtAsc(QuizStatus status);

  /** Quizzes in a review state, most recently reviewed first. */
  List<QuizEntity> findByStatusOrderByReviewedAtDesc(QuizStatus status);

  /** Each locality id with its number of quizzes in {@code status}; rows are {id, count}. */
  @Query(
      "SELECT q.locality.id, COUNT(q) FROM QuizEntity q WHERE q.status = :status GROUP BY q.locality.id")
  List<Object[]> countByLocalityForStatus(@Param("status") QuizStatus status);

  /**
   * One random QUEUED quiz for the locality, row-locked so two concurrent runs cannot pick the same
   * quiz. Must run inside a transaction.
   */
  @Query(
      value =
          "SELECT * FROM quizzes WHERE locality_id = :localityId AND status = 'QUEUED'"
              + " ORDER BY random() LIMIT 1 FOR UPDATE SKIP LOCKED",
      nativeQuery = true)
  Optional<QuizEntity> lockRandomQueuedQuiz(@Param("localityId") Long localityId);
}
