package org.smalltech.hashtaglocal_backend.model.response;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import lombok.Builder;
import lombok.Data;

/**
 * A locality's daily bulletin as served to users (snake_case): weather + AI summary + the quiz
 * (answer hidden) + the viewer's attempt status. Reused both by {@code GET /api/v1/bulletin} and
 * nested inside BULLETIN feed posts.
 */
@Data
@Builder
public class BulletinData {

  private Long id;
  private Long localityId;
  private String hashtag;
  private String localityName;
  private LocalDate date;

  /** Weather payload: min_temp, max_temp, humidity, rain_probability, avg_aqi, pollen. */
  private Map<String, Object> weather;

  private String weatherSource;

  /** AI (ops-editable) summary text. */
  private String summary;

  private QuizData quiz;

  @Data
  @Builder
  public static class QuizData {
    private Long id;
    private String question;

    /** Four option texts in display order. The correct index is never exposed before an attempt. */
    private List<String> options;

    /** Viewer's attempt; null when unauthenticated or not yet attempted. */
    private AttemptData attempt;
  }

  @Data
  @Builder
  public static class AttemptData {
    private boolean isCorrect;

    /** 1-based; null = timed out without answering. */
    private Integer selectedOptionIndex;

    /** Revealed once attempted. */
    private Integer answerOptionIndex;

    private String explanation;
  }
}
