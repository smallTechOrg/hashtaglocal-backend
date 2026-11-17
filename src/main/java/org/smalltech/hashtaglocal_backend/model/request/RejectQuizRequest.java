package org.smalltech.hashtaglocal_backend.model.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;
import org.smalltech.hashtaglocal_backend.model.QuizRejectReason;

/**
 * Admin rejection of one pending quiz: a reason, plus a note (required when the reason is OTHER).
 */
@Data
public class RejectQuizRequest {

  @NotNull private QuizRejectReason reason;

  @Size(max = 300)
  private String note;
}
