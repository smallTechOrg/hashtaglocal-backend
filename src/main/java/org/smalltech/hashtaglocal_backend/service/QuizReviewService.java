package org.smalltech.hashtaglocal_backend.service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.smalltech.hashtaglocal_backend.entity.Locality;
import org.smalltech.hashtaglocal_backend.entity.QuizEntity;
import org.smalltech.hashtaglocal_backend.exception.DownstreamServiceException;
import org.smalltech.hashtaglocal_backend.mapper.BulletinViewMapper;
import org.smalltech.hashtaglocal_backend.model.QuizRejectReason;
import org.smalltech.hashtaglocal_backend.model.QuizStatus;
import org.smalltech.hashtaglocal_backend.model.response.AdminQuizData;
import org.smalltech.hashtaglocal_backend.model.response.QueuedQuizCountData;
import org.smalltech.hashtaglocal_backend.repository.QuizRepository;
import org.smalltech.hashtaglocal_backend.repository.UserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Admin review of AI-generated quizzes (#local Quiz Automation, "Flows 1-3"): generate, list the
 * pending ones, then approve (to QUEUED) or reject (with a reason). Rejecting also generates a
 * replacement quiz for the same locality.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class QuizReviewService {

  private final QuizRepository quizRepository;
  private final UserRepository userRepository;
  private final QuizGenerationService quizGenerationService;

  /** Generates one PENDING quiz per saved-user locality, or only for {@code hashtag} if given. */
  public List<AdminQuizData> generate(String hashtag) {
    List<Locality> localities = userRepository.findDistinctUserLocalities();
    if (hashtag != null && !hashtag.isBlank()) {
      localities =
          localities.stream().filter(l -> hashtag.equalsIgnoreCase(l.getHashtag())).toList();
      if (localities.isEmpty()) {
        throw new DownstreamServiceException(
            HttpStatus.NOT_FOUND, "NOT_FOUND", "No saved-user locality with hashtag: " + hashtag);
      }
    }

    List<AdminQuizData> generated = new ArrayList<>();
    for (Locality locality : localities) {
      quizGenerationService
          .generatePendingQuiz(locality)
          .map(this::toData)
          .ifPresent(generated::add);
    }
    return generated;
  }

  @Transactional(readOnly = true)
  public List<AdminQuizData> pending() {
    return quizRepository
        .findByStatusOrderByLocality_HashtagAscCreatedAtAsc(QuizStatus.PENDING)
        .stream()
        .map(this::toData)
        .toList();
  }

  /** Queued quizzes per saved-user locality, including localities with none queued (0). */
  @Transactional(readOnly = true)
  public List<QueuedQuizCountData> queuedCounts() {
    Map<Long, Long> counts = new HashMap<>();
    for (Object[] row : quizRepository.countByLocalityForStatus(QuizStatus.QUEUED)) {
      counts.put((Long) row[0], (Long) row[1]);
    }
    return userRepository.findDistinctUserLocalities().stream()
        .map(
            l ->
                QueuedQuizCountData.builder()
                    .localityId(l.getId())
                    .localityName(l.getName())
                    .hashtag(l.getHashtag())
                    .queued(counts.getOrDefault(l.getId(), 0L))
                    .build())
        .sorted(Comparator.comparing(QueuedQuizCountData::getHashtag))
        .toList();
  }

  /** Rejected quizzes, most recently reviewed first. Optionally filtered by hashtag and reason. */
  @Transactional(readOnly = true)
  public List<AdminQuizData> rejected(String hashtag, QuizRejectReason reason) {
    return quizRepository.findByStatusOrderByReviewedAtDesc(QuizStatus.REJECTED).stream()
        .filter(
            quiz ->
                hashtag == null
                    || hashtag.isBlank()
                    || hashtag.equalsIgnoreCase(quiz.getLocality().getHashtag()))
        .filter(quiz -> reason == null || reason == quiz.getRejectionReason())
        .map(this::toData)
        .toList();
  }

  /** Approves the given pending quizzes. Ids that are not pending are ignored. */
  @Transactional
  public List<AdminQuizData> approve(List<Long> quizIds, Long adminUserId) {
    LocalDateTime now = LocalDateTime.now();
    return quizRepository.findAllById(quizIds).stream()
        .filter(quiz -> quiz.getStatus() == QuizStatus.PENDING)
        .map(
            quiz -> {
              quiz.setStatus(QuizStatus.QUEUED);
              review(quiz, adminUserId, now);
              return toData(quiz);
            })
        .toList();
  }

  /** Rejects a pending quiz. A note is required when the reason is OTHER. */
  @Transactional
  public AdminQuizData reject(Long quizId, QuizRejectReason reason, String note, Long adminUserId) {
    String cleanNote = note == null || note.isBlank() ? null : note.trim();
    if (reason == QuizRejectReason.OTHER && cleanNote == null) {
      throw new DownstreamServiceException(
          HttpStatus.BAD_REQUEST,
          "VALIDATION_ERROR",
          "A note is required when the reason is Other.");
    }
    QuizEntity quiz =
        quizRepository
            .findById(quizId)
            .orElseThrow(
                () ->
                    new DownstreamServiceException(
                        HttpStatus.NOT_FOUND, "NOT_FOUND", "Unknown quiz: " + quizId));
    if (quiz.getStatus() != QuizStatus.PENDING) {
      throw new IllegalStateException("Only pending quizzes can be rejected.");
    }
    quiz.setStatus(QuizStatus.REJECTED);
    quiz.setRejectionReason(reason);
    quiz.setRejectionNote(cleanNote);
    review(quiz, adminUserId, LocalDateTime.now());
    // Replacement for the same locality. A failed generation does not undo the rejection; the
    // new question lands in the pending list, or ops can click generate again.
    quizGenerationService.generatePendingQuiz(quiz.getLocality());
    return toData(quiz);
  }

  private static void review(QuizEntity quiz, Long adminUserId, LocalDateTime at) {
    quiz.setReviewedBy(adminUserId);
    quiz.setReviewedAt(at);
  }

  /** Pending and queued quizzes have no bulletin date yet, so the date is left empty. */
  private AdminQuizData toData(QuizEntity quiz) {
    return AdminQuizData.builder()
        .id(quiz.getId())
        .localityId(quiz.getLocality().getId())
        .localityName(quiz.getLocality().getName())
        .hashtag(quiz.getLocality().getHashtag())
        .question(quiz.getQuestion())
        .options(BulletinViewMapper.optionTexts(quiz))
        .answerOptionIndex(quiz.getAnswerOptionIndex())
        .explanation(quiz.getExplanation())
        .status(quiz.getStatus())
        .rejectionReason(quiz.getRejectionReason())
        .rejectionNote(quiz.getRejectionNote())
        .reviewedBy(quiz.getReviewedBy())
        .reviewedAt(quiz.getReviewedAt())
        .build();
  }
}
