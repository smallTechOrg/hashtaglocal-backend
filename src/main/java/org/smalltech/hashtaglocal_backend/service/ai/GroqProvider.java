package org.smalltech.hashtaglocal_backend.service.ai;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * Groq chat completions (OpenAI-compatible). Limits are per organization <em>and per model</em>, so
 * switching model gives fresh quota. Separate from the bulletin's {@code GroqClient} so the two
 * never share a key's quota.
 */
@Component
public class GroqProvider extends RestAiProvider {

  private static final String URL = "https://api.groq.com/openai/v1/chat/completions";

  // A wait longer than this means a daily limit was hit, not a per-minute one.
  private static final Duration DAILY_LIMIT_THRESHOLD = Duration.ofSeconds(60);
  // "Request too large" is the per-minute token cap for this model; another model may fit.
  private static final Duration TOO_LARGE_COOLDOWN = Duration.ofMinutes(10);

  // Reasoning models may put their thinking before the answer; the quiz parser wants only the
  // answer.
  private static final Pattern THINK_BLOCK = Pattern.compile("(?s)<think>.*?</think>");

  public GroqProvider(RestClient.Builder restClientBuilder, ObjectMapper objectMapper) {
    super(restClientBuilder, objectMapper);
  }

  @Override
  public String name() {
    return "groq";
  }

  @Override
  protected String send(String model, String apiKey, String prompt, int maxTokens) {
    Map<String, Object> body =
        Map.of(
            "model",
            model,
            "messages",
            List.of(Map.of("role", "user", "content", prompt)),
            "temperature",
            0.7,
            "max_tokens",
            maxTokens);
    return restClient
        .post()
        .uri(URL)
        .header(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey)
        .contentType(MediaType.APPLICATION_JSON)
        .body(body)
        .retrieve()
        .body(String.class);
  }

  @Override
  protected String extractText(String raw) throws Exception {
    String content =
        objectMapper
            .readTree(raw)
            .path("choices")
            .path(0)
            .path("message")
            .path("content")
            .asText("");
    return THINK_BLOCK.matcher(content).replaceAll("");
  }

  @Override
  AiResult classify(int status, HttpHeaders headers, String body) {
    String detail = detail(status, body);
    return switch (status) {
      case 429 -> {
        Duration wait = retryAfter(headers);
        boolean daily =
            containsIgnoreCase(body, "per day")
                || (wait != null && wait.compareTo(DAILY_LIMIT_THRESHOLD) > 0);
        yield AiResult.failure(
            daily ? AiFailure.QUOTA_EXHAUSTED : AiFailure.RATE_LIMITED, wait, detail);
      }
      case 401, 403 -> AiResult.keyFailure(AiFailure.AUTH_FAILED, detail);
      case 404 -> AiResult.failure(AiFailure.MODEL_UNAVAILABLE, detail);
      case 413 -> AiResult.failure(AiFailure.RATE_LIMITED, TOO_LARGE_COOLDOWN, detail);
      case 400, 422 ->
          containsIgnoreCase(body, "decommissioned") || containsIgnoreCase(body, "model_not_found")
              ? AiResult.failure(AiFailure.MODEL_UNAVAILABLE, detail)
              : AiResult.failure(AiFailure.BAD_REQUEST, detail);
      // 498 capacity, 500, 503 and anything else unexpected.
      default -> AiResult.failure(AiFailure.TRANSIENT, detail);
    };
  }
}
