package org.smalltech.hashtaglocal_backend.service;

import java.time.LocalDate;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.smalltech.hashtaglocal_backend.entity.BulletinEntity;
import org.smalltech.hashtaglocal_backend.entity.Locality;
import org.smalltech.hashtaglocal_backend.entity.QuizEntity;
import org.smalltech.hashtaglocal_backend.model.QuizStatus;
import org.smalltech.hashtaglocal_backend.repository.BulletinRepository;
import org.smalltech.hashtaglocal_backend.repository.QuizRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The 8 AM quiz pick (#local Quiz Automation, "Flow 4"): attaches one random QUEUED quiz to a
 * locality's bulletin for the day and marks it PUBLISHED. An empty queue means no quiz that day.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class QuizQueueService {

  private final QuizRepository quizRepository;
  private final BulletinRepository bulletinRepository;

  /** Publishes a quiz for {@code date} if the bulletin has none yet. Returns the quiz, if any. */
  @Transactional
  public Optional<QuizEntity> publishForDate(Locality locality, LocalDate date) {
    Optional<BulletinEntity> existing =
        bulletinRepository.findByLocalityIdAndDate(locality.getId(), date);
    if (existing.isPresent() && existing.get().getQuiz() != null) {
      return Optional.ofNullable(existing.get().getQuiz());
    }

    Optional<QuizEntity> picked = quizRepository.lockRandomQueuedQuiz(locality.getId());
    if (picked.isEmpty()) {
      log.warn("Quiz queue is empty for {} on {}; no quiz today", locality.getHashtag(), date);
      return Optional.empty();
    }

    QuizEntity quiz = picked.get();
    quiz.setStatus(QuizStatus.PUBLISHED);
    BulletinEntity bulletin =
        existing.orElseGet(() -> BulletinEntity.builder().locality(locality).date(date).build());
    bulletin.setQuiz(quiz);
    bulletinRepository.save(bulletin);
    log.info("Published quiz {} for {} on {}", quiz.getId(), locality.getHashtag(), date);
    return Optional.of(quiz);
  }
}
