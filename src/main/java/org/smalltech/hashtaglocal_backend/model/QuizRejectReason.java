package org.smalltech.hashtaglocal_backend.model;

/** Why an admin rejected a generated quiz. Stored on {@code quizzes.rejection_reason}. */
public enum QuizRejectReason {
  /** A fact, the marked answer or the explanation is wrong. */
  INACCURATE,
  /** The question is not about this locality. */
  NOT_LOCAL,
  /** Unclear question, weak options, or too easy or too hard. */
  LOW_QUALITY,
  /** Repeats or closely paraphrases an earlier question. */
  DUPLICATE,
  /** Anything else; a note is required. */
  OTHER
}
