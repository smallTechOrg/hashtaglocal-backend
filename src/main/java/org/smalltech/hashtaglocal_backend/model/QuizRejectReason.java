package org.smalltech.hashtaglocal_backend.model;

/** Why an admin rejected a generated quiz. Stored on {@code quizzes.rejection_reason}. */
public enum QuizRejectReason {
  WRONG_ANSWER,
  UNCLEAR_QUESTION,
  POOR_OPTIONS,
  NOT_LOCAL,
  TOO_EASY,
  TOO_HARD,
  REPEAT,
  OTHER
}
