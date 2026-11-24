package org.smalltech.hashtaglocal_backend.service.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * Gemini generateContent API. Free-tier quota is per Google Cloud project <em>and per model</em>,
 * so each key must come from its own project. Separate from feed moderation's Gemini key.
 */
@Component
public class GeminiProvider extends RestAiProvider {

  /** Base URL; the model and ":generateContent" are appended. */
  static final String DEFAULT_URL = "https://generativelanguage.googleapis.com/v1beta/models";

  public GeminiProvider(RestClient.Builder restClientBuilder, ObjectMapper objectMapper) {
    super(restClientBuilder, objectMapper);
  }

  @Override
  public String name() {
    return "gemini";
  }

  @Override
  public String displayName() {
    return "Gemini (Google)";
  }

  @Override
  public String defaultEndpoint() {
    return DEFAULT_URL;
  }

  @Override
  protected String send(String url, String model, String apiKey, String prompt, int maxTokens) {
    Map<String, Object> body =
        Map.of(
            "contents",
            List.of(Map.of("role", "user", "parts", List.of(Map.of("text", prompt)))),
            "generationConfig",
            Map.of("maxOutputTokens", maxTokens, "temperature", 0.7));
    // Key in a header, not the URL, so it can never end up in a logged URL.
    return restClient
        .post()
        .uri(stripTrailingSlash(url) + "/{model}:generateContent", model)
        .header("x-goog-api-key", apiKey)
        .contentType(MediaType.APPLICATION_JSON)
        .body(body)
        .retrieve()
        .body(String.class);
  }

  @Override
  protected String extractText(String raw) throws Exception {
    StringBuilder text = new StringBuilder();
    JsonNode parts = objectMapper.readTree(raw).path("candidates").path(0).path("content");
    for (JsonNode part : parts.path("parts")) {
      if (!part.path("thought").asBoolean(false)) {
        text.append(part.path("text").asText(""));
      }
    }
    return text.toString();
  }

  @Override
  AiResult classify(int status, HttpHeaders headers, String body) {
    String detail = detail(status, body);
    return switch (status) {
      case 429 -> {
        Duration wait = retryDelay(body);
        yield containsIgnoreCase(body, "PerDay")
            ? AiResult.failure(AiFailure.QUOTA_EXHAUSTED, detail)
            : AiResult.failure(AiFailure.RATE_LIMITED, wait, detail);
      }
      case 400 ->
          // A bad key, or a key that cannot use the API at all (e.g. region or billing), comes back
          // as 400 rather than 401.
          containsIgnoreCase(body, "API_KEY_INVALID")
                  || containsIgnoreCase(body, "API key not valid")
                  || containsIgnoreCase(body, "FAILED_PRECONDITION")
              ? AiResult.keyFailure(AiFailure.AUTH_FAILED, detail)
              : AiResult.failure(AiFailure.BAD_REQUEST, detail);
      case 401, 403 -> AiResult.keyFailure(AiFailure.AUTH_FAILED, detail);
      case 404 -> AiResult.failure(AiFailure.MODEL_UNAVAILABLE, detail);
      // 500, 503 overloaded, 504 and anything else unexpected.
      default -> AiResult.failure(AiFailure.TRANSIENT, detail);
    };
  }

  private static String stripTrailingSlash(String url) {
    return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
  }

  /** The wait from the error's RetryInfo detail (e.g. "37s" or "12.5s"), or null. */
  private Duration retryDelay(String body) {
    try {
      for (JsonNode detail : objectMapper.readTree(body).path("error").path("details")) {
        String delay = detail.path("retryDelay").asText("");
        if (delay.endsWith("s")) {
          double seconds = Double.parseDouble(delay.substring(0, delay.length() - 1));
          return Duration.ofSeconds((long) Math.ceil(seconds));
        }
      }
    } catch (Exception e) {
      // Not JSON or no RetryInfo; the chain's default cooldown applies.
    }
    return null;
  }
}
