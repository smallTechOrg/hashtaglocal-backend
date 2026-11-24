package org.smalltech.hashtaglocal_backend.service.ai;

import java.util.ArrayList;
import java.util.List;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * {@code ai.quiz.chain}: the providers for quiz generation, in fallback order. Each level lists its
 * models (best first) and keys; both bind from comma-separated env vars.
 */
@Configuration
@ConfigurationProperties(prefix = "ai.quiz")
@Data
public class QuizAiProperties {

  private List<Level> chain = new ArrayList<>();

  @Data
  public static class Level {
    /** Provider name: anthropic, openai, groq or gemini (openai-compatible needs the dashboard). */
    private String provider;

    /** Model ids, best first. */
    private List<String> models = new ArrayList<>();

    /** API keys, each from a separate account or project. */
    private List<String> keys = new ArrayList<>();
  }
}
