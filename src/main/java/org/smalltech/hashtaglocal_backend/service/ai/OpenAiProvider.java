package org.smalltech.hashtaglocal_backend.service.ai;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * OpenAI Chat Completions API. Uses {@code max_completion_tokens} and the default temperature,
 * since current (reasoning) models reject {@code max_tokens} and custom temperatures.
 */
@Component
public class OpenAiProvider extends RestAiProvider {

  static final String DEFAULT_URL = "https://api.openai.com/v1/chat/completions";

  public OpenAiProvider(RestClient.Builder restClientBuilder, ObjectMapper objectMapper) {
    super(restClientBuilder, objectMapper);
  }

  @Override
  public String name() {
    return "openai";
  }

  @Override
  public String displayName() {
    return "OpenAI";
  }

  @Override
  public String defaultEndpoint() {
    return DEFAULT_URL;
  }

  @Override
  protected String send(String url, String model, String apiKey, String prompt, int maxTokens) {
    Map<String, Object> body =
        Map.of(
            "model",
            model,
            "messages",
            List.of(Map.of("role", "user", "content", prompt)),
            "max_completion_tokens",
            maxTokens);
    return restClient
        .post()
        .uri(url)
        .header(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey)
        .contentType(MediaType.APPLICATION_JSON)
        .body(body)
        .retrieve()
        .body(String.class);
  }

  @Override
  protected String extractText(String raw) throws Exception {
    return objectMapper
        .readTree(raw)
        .path("choices")
        .path(0)
        .path("message")
        .path("content")
        .asText("");
  }

  @Override
  AiResult classify(int status, HttpHeaders headers, String body) {
    String detail = detail(status, body);
    // No credit / billing limit is about the whole account, so every model on this key is out.
    if (containsIgnoreCase(body, "insufficient_quota")
        || containsIgnoreCase(body, "billing_hard_limit")) {
      return AiResult.keyFailure(AiFailure.QUOTA_EXHAUSTED, detail);
    }
    return switch (status) {
      case 429 -> AiResult.failure(AiFailure.RATE_LIMITED, retryAfter(headers), detail);
      case 401, 403 -> AiResult.keyFailure(AiFailure.AUTH_FAILED, detail);
      case 404 -> AiResult.failure(AiFailure.MODEL_UNAVAILABLE, detail);
      case 400, 413, 422 ->
          containsIgnoreCase(body, "model_not_found")
              ? AiResult.failure(AiFailure.MODEL_UNAVAILABLE, detail)
              : AiResult.failure(AiFailure.BAD_REQUEST, detail);
      // 500, 503 and anything else unexpected.
      default -> AiResult.failure(AiFailure.TRANSIENT, detail);
    };
  }
}
