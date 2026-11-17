package org.smalltech.hashtaglocal_backend.model.response;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import lombok.Builder;
import lombok.Data;
import org.smalltech.hashtaglocal_backend.model.QuizRejectReason;
import org.smalltech.hashtaglocal_backend.model.QuizStatus;

/** A quiz row in the ops portal, with its bulletin date and full answer key. */
@Data
@Builder
public class AdminQuizData {

  private Long id;
  private Long localityId;
  private String localityName;
  private String hashtag;

  /** Serving date, from the linked bulletin row. */
  private LocalDate date;

  private String question;
  private List<String> options;
  private Integer answerOptionIndex;
  private String explanation;

  /** Review state: PENDING, QUEUED, REJECTED or PUBLISHED. */
  private QuizStatus status;

  /** Why the quiz was rejected; null unless {@code status} is REJECTED. */
  private QuizRejectReason rejectionReason;

  /** The admin's note on the rejection; null unless the quiz was rejected with one. */
  private String rejectionNote;

  /** Admin user id who approved or rejected the quiz; null while pending. */
  private Long reviewedBy;

  /** When the admin approved or rejected the quiz; null while pending. */
  private LocalDateTime reviewedAt;

  /** True once any user has attempted — deletion is blocked from then on. */
  private boolean hasAttempts;
}
