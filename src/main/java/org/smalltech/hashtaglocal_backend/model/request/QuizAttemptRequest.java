package org.smalltech.hashtaglocal_backend.model.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.Data;

/** A user's quiz answer. {@code null} selectedOptionIndex records a missed (timed-out) attempt. */
@Data
public class QuizAttemptRequest {

  @Min(1)
  @Max(4)
  private Integer selectedOptionIndex;
}
