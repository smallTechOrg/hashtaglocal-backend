package org.smalltech.hashtaglocal_backend.model.response;

import java.time.LocalDate;
import java.util.List;
import lombok.Builder;
import lombok.Data;

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

  /** True once any user has attempted — deletion is blocked from then on. */
  private boolean hasAttempts;
}
