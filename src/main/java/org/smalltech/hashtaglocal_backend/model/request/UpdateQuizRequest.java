package org.smalltech.hashtaglocal_backend.model.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import java.util.List;
import lombok.Data;

/** Admin quiz edit; only the provided fields change. */
@Data
public class UpdateQuizRequest {

  private String question;

  @Size(min = 4, max = 4)
  private List<String> options;

  @Min(1)
  @Max(4)
  private Integer answerOptionIndex;

  /** Direct ops edit of the explanation text. Ignored when {@code regenerateExplanation} is set. */
  private String explanation;

  /** When true, Groq regenerates the explanation from the (updated) question and answer. */
  private Boolean regenerateExplanation;
}
