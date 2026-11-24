package org.smalltech.hashtaglocal_backend.service.ai;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * Any server that speaks the OpenAI Chat Completions format (OpenRouter, Together, DeepSeek,
 * Mistral, Fireworks, vLLM, ...). Differs from {@link OpenAiProvider} in three ways:
 *
 * <ul>
 *   <li>No default endpoint: the admin must enter one. A base URL ({@code https://host/v1}) gets
 *       {@code /chat/completions} appended; a full URL is used as is.
 *   <li>Sends the widely supported {@code max_tokens}, not OpenAI's newer {@code
 *       max_completion_tokens}.
 *   <li>Strips {@code <think>...</think>} blocks that reasoning models put before the answer, so
 *       the quiz parser sees only the answer.
 * </ul>
 */
@Component
public class OpenAiCompatibleProvider extends RestAiProvider {

  static final String CHAT_PATH = "/chat/completions";

  private static final Pattern THINK_BLOCK = Pattern.compile("(?s)<think>.*?</think>");

  public OpenAiCompatibleProvider(RestClient.Builder restClientBuilder, ObjectMapper objectMapper) {
    super(restClientBuilder, objectMapper);
  }

  @Override
  public String name() {
    return "openai-compatible";
  }

  @Override
  public String displayName() {
    return "OpenAI-compatible (custom endpoint)";
  }

  /** None: every server is different, so the endpoint must be entered. */
  @Override
  public String defaultEndpoint() {
    return null;
  }

  @Override
  protected String send(String url, String model, String apiKey, String prompt, int maxTokens) {
    return restClient
        .post()
        .uri(chatUrl(url))
        .header(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey)
        .contentType(MediaType.APPLICATION_JSON)
        .body(body(model, prompt, maxTokens))
        .retrieve()
        .body(String.class);
  }

  static Map<String, Object> body(String model, String prompt, int maxTokens) {
    return Map.of(
        "model",
        model,
        "messages",
        List.of(Map.of("role", "user", "content", prompt)),
        "max_tokens",
        maxTokens);
  }

  /** {@code https://host/v1} → {@code https://host/v1/chat/completions}; a full URL is kept. */
  static String chatUrl(String url) {
    String trimmed = url.trim();
    while (trimmed.endsWith("/")) {
      trimmed = trimmed.substring(0, trimmed.length() - 1);
    }
    return trimmed.endsWith(CHAT_PATH) ? trimmed : trimmed + CHAT_PATH;
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
    if (status == 402
        || containsIgnoreCase(body, "insufficient_quota")
        || containsIgnoreCase(body, "insufficient credits")) {
      return AiResult.keyFailure(AiFailure.QUOTA_EXHAUSTED, detail);
    }
    return switch (status) {
      case 429 -> AiResult.failure(AiFailure.RATE_LIMITED, retryAfter(headers), detail);
      case 401, 403 -> AiResult.keyFailure(AiFailure.AUTH_FAILED, detail);
      // Unknown model, or a wrong endpoint path; the detail shows which.
      case 404 -> AiResult.failure(AiFailure.MODEL_UNAVAILABLE, detail);
      case 400, 413, 422 ->
          containsIgnoreCase(body, "model_not_found")
                  || containsIgnoreCase(body, "model not found")
                  || containsIgnoreCase(body, "does not exist")
              ? AiResult.failure(AiFailure.MODEL_UNAVAILABLE, detail)
              : AiResult.failure(AiFailure.BAD_REQUEST, detail);
      default -> AiResult.failure(AiFailure.TRANSIENT, detail);
    };
  }
}
