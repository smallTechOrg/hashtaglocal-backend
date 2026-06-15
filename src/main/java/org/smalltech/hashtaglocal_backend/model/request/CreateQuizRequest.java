package org.smalltech.hashtaglocal_backend.model.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.util.List;
import lombok.Data;

/**
 * Admin request to create the quiz for a locality + date (the date binding lives on the bulletin
 * row, which is upserted with the new quiz).
 */
@Data
public class CreateQuizRequest {

  @NotNull private Long localityId;

  /** The day this quiz is served (today or a future date the ops team is preparing). */
  @NotNull private LocalDate date;

  @NotBlank private String question;

  /** Exactly four option texts, in display order. */
  @NotNull
  @Size(min = 4, max = 4)
  private List<String> options;

  /** 1-based index of the correct option. */
  @NotNull
  @Min(1)
  @Max(4)
  private Integer answerOptionIndex;

  /** Optional ops-written explanation; when absent, Groq generates one. */
  private String explanation;
}
