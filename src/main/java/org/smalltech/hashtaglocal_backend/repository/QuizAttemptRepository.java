package org.smalltech.hashtaglocal_backend.repository;

import java.util.Optional;
import org.smalltech.hashtaglocal_backend.entity.QuizAttemptEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface QuizAttemptRepository extends JpaRepository<QuizAttemptEntity, Long> {

  Optional<QuizAttemptEntity> findByUserIdAndQuizId(Long userId, Long quizId);

  boolean existsByQuizId(Long quizId);
}
