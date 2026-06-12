package org.smalltech.hashtaglocal_backend.service;

import java.time.LocalDate;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.smalltech.hashtaglocal_backend.entity.Locality;
import org.smalltech.hashtaglocal_backend.entity.QuizAttemptEntity;
import org.smalltech.hashtaglocal_backend.entity.QuizEntity;
import org.smalltech.hashtaglocal_backend.exception.DownstreamServiceException;
import org.smalltech.hashtaglocal_backend.mapper.BulletinViewMapper;
import org.smalltech.hashtaglocal_backend.model.response.BulletinData;
import org.smalltech.hashtaglocal_backend.model.response.QuizAttemptResultData;
import org.smalltech.hashtaglocal_backend.repository.BulletinRepository;
import org.smalltech.hashtaglocal_backend.repository.LocalityRepository;
import org.smalltech.hashtaglocal_backend.repository.QuizAttemptRepository;
import org.smalltech.hashtaglocal_backend.repository.QuizRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * User-facing bulletin reads and quiz attempts. Strictly read-or-record: nothing is generated here
 * — a locality with no bulletin today simply gets an empty answer until tomorrow's 8 AM job.
 */
@Service
@RequiredArgsConstructor
public class BulletinService {

  private final BulletinRepository bulletinRepository;
  private final LocalityRepository localityRepository;
  private final QuizRepository quizRepository;
  private final QuizAttemptRepository quizAttemptRepository;
  private final BulletinViewMapper bulletinViewMapper;

  /** Today's bulletin for a locality (by id or hashtag); empty when not generated yet. */
  @Transactional(readOnly = true)
  public Optional<BulletinData> getTodayBulletin(
      Long localityId, String hashtag, Long viewerUserId) {
    Long resolvedLocalityId = resolveLocalityId(localityId, hashtag);
    return bulletinRepository
        .findByLocalityIdAndDate(resolvedLocalityId, LocalDate.now())
        .map(bulletin -> bulletinViewMapper.toData(bulletin, viewerUserId));
  }

  /**
   * Records the user's single attempt. {@code selectedOptionIndex == null} = the 15-second timer
   * expired — recorded as a missed (incorrect) attempt, no retry.
   */
  @Transactional
  public QuizAttemptResultData submitAttempt(
      Long userId, Long quizId, Integer selectedOptionIndex) {
    QuizEntity quiz =
        quizRepository
            .findById(quizId)
            .orElseThrow(
                () ->
                    new DownstreamServiceException(
                        HttpStatus.NOT_FOUND, "NOT_FOUND", "Unknown quiz: " + quizId));

    if (quizAttemptRepository.findByUserIdAndQuizId(userId, quizId).isPresent()) {
      throw new IllegalStateException("You have already attempted this quiz.");
    }

    boolean isCorrect =
        selectedOptionIndex != null && selectedOptionIndex.equals(quiz.getAnswerOptionIndex());

    QuizAttemptEntity attempt =
        QuizAttemptEntity.builder()
            .userId(userId)
            .quiz(quiz)
            .selectedOptionIndex(selectedOptionIndex)
            .isCorrect(isCorrect)
            .build();
    try {
      quizAttemptRepository.saveAndFlush(attempt);
    } catch (DataIntegrityViolationException e) {
      // Concurrent double-submit lost the race against the unique (user_id, quiz_id) constraint.
      throw new IllegalStateException("You have already attempted this quiz.");
    }

    return QuizAttemptResultData.builder()
        .quizId(quizId)
        .isCorrect(isCorrect)
        .selectedOptionIndex(selectedOptionIndex)
        .answerOptionIndex(quiz.getAnswerOptionIndex())
        .explanation(quiz.getExplanation())
        .build();
  }

  private Long resolveLocalityId(Long localityId, String hashtag) {
    if (localityId != null) {
      return localityId;
    }
    if (hashtag != null && !hashtag.isBlank()) {
      return localityRepository
          .findByHashtagFlexible(hashtag)
          .map(Locality::getId)
          .orElseThrow(
              () ->
                  new DownstreamServiceException(
                      HttpStatus.NOT_FOUND, "NOT_FOUND", "Unknown hashtag: " + hashtag));
    }
    throw new IllegalArgumentException("Either locality_id or hashtag is required");
  }
}
