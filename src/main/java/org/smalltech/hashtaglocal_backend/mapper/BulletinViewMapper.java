package org.smalltech.hashtaglocal_backend.mapper;

import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.smalltech.hashtaglocal_backend.entity.BulletinEntity;
import org.smalltech.hashtaglocal_backend.entity.QuizAttemptEntity;
import org.smalltech.hashtaglocal_backend.entity.QuizEntity;
import org.smalltech.hashtaglocal_backend.model.response.BulletinData;
import org.smalltech.hashtaglocal_backend.repository.QuizAttemptRepository;
import org.springframework.stereotype.Component;

/**
 * Maps a {@link BulletinEntity} to the user-facing {@link BulletinData}: weather + summary + quiz
 * with the answer hidden until the viewer has attempted. Shared by the bulletin endpoint and the
 * BULLETIN feed-post payload so both entry points behave identically.
 */
@Component
@RequiredArgsConstructor
public class BulletinViewMapper {

  private final QuizAttemptRepository quizAttemptRepository;

  public BulletinData toData(BulletinEntity bulletin, Long viewerUserId) {
    BulletinData.BulletinDataBuilder b =
        BulletinData.builder()
            .id(bulletin.getId())
            .localityId(bulletin.getLocality().getId())
            .hashtag(bulletin.getLocality().getHashtag())
            .localityName(bulletin.getLocality().getName())
            .date(bulletin.getDate())
            .summary(summaryText(bulletin));

    if (bulletin.getPeriodicData() != null) {
      b.weather(bulletin.getPeriodicData().getData())
          .weatherSource(bulletin.getPeriodicData().getSource());
    }

    QuizEntity quiz = bulletin.getQuiz();
    if (quiz != null) {
      b.quiz(
          BulletinData.QuizData.builder()
              .id(quiz.getId())
              .question(quiz.getQuestion())
              .options(optionTexts(quiz))
              .attempt(attemptOf(quiz, viewerUserId))
              .build());
    }
    return b.build();
  }

  /** Extracts the display texts from the four JSONB option maps. */
  public static List<String> optionTexts(QuizEntity quiz) {
    return List.of(
        optionText(quiz.getOption1()),
        optionText(quiz.getOption2()),
        optionText(quiz.getOption3()),
        optionText(quiz.getOption4()));
  }

  private static String optionText(Map<String, Object> option) {
    Object text = option != null ? option.get("text") : null;
    return text != null ? text.toString() : "";
  }

  private static String summaryText(BulletinEntity bulletin) {
    Object text = bulletin.getSummary() != null ? bulletin.getSummary().get("text") : null;
    return text != null ? text.toString() : null;
  }

  private BulletinData.AttemptData attemptOf(QuizEntity quiz, Long viewerUserId) {
    if (viewerUserId == null) {
      return null;
    }
    return quizAttemptRepository
        .findByUserIdAndQuizId(viewerUserId, quiz.getId())
        .map(
            attempt ->
                BulletinData.AttemptData.builder()
                    .isCorrect(attempt.isCorrect())
                    .selectedOptionIndex(attempt.getSelectedOptionIndex())
                    .answerOptionIndex(quiz.getAnswerOptionIndex())
                    .explanation(quiz.getExplanation())
                    .build())
        .orElse(null);
  }

  /** Result payload for a just-submitted attempt. */
  public static BulletinData.AttemptData toAttemptData(QuizAttemptEntity attempt, QuizEntity quiz) {
    return BulletinData.AttemptData.builder()
        .isCorrect(attempt.isCorrect())
        .selectedOptionIndex(attempt.getSelectedOptionIndex())
        .answerOptionIndex(quiz.getAnswerOptionIndex())
        .explanation(quiz.getExplanation())
        .build();
  }
}
