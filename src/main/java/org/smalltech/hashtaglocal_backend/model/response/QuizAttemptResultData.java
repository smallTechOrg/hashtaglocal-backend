package org.smalltech.hashtaglocal_backend.model.response;

import lombok.Builder;
import lombok.Data;

/** Result of submitting a quiz attempt: verdict, the correct answer, and the explanation. */
@Data
@Builder
public class QuizAttemptResultData {

  private Long quizId;
  private boolean isCorrect;

  /** 1-based; null when the attempt was a timeout. */
  private Integer selectedOptionIndex;

  private Integer answerOptionIndex;
  private String explanation;
}
