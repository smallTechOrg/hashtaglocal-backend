package org.smalltech.hashtaglocal_backend.service.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/** Anthropic Messages API (Claude). Credit and rate limits belong to the organization. */
@Component
public class AnthropicProvider extends RestAiProvider {

  private static final String URL = "https://api.anthropic.com/v1/messages";
  private static final String API_VERSION = "2023-06-01";

  public AnthropicProvider(RestClient.Builder restClientBuilder, ObjectMapper objectMapper) {
    super(restClientBuilder, objectMapper);
  }

  @Override
  public String name() {
    return "anthropic";
  }

  @Override
  protected String send(String model, String apiKey, String prompt, int maxTokens) {
    Map<String, Object> body =
        Map.of(
            "model",
            model,
            "max_tokens",
            maxTokens,
            "messages",
            List.of(Map.of("role", "user", "content", prompt)));
    return restClient
        .post()
        .uri(URL)
        .header("x-api-key", apiKey)
        .header("anthropic-version", API_VERSION)
        .contentType(MediaType.APPLICATION_JSON)
        .body(body)
        .retrieve()
        .body(String.class);
  }

  @Override
  protected String extractText(String raw) throws Exception {
    StringBuilder text = new StringBuilder();
    for (JsonNode block : objectMapper.readTree(raw).path("content")) {
      if ("text".equals(block.path("type").asText())) {
        text.append(block.path("text").asText(""));
      }
    }
    return text.toString();
  }

  @Override
  AiResult classify(int status, HttpHeaders headers, String body) {
    String detail = detail(status, body);
    // No credit left is about the whole organization, so every model on this key is out.
    if (status == 402
        || containsIgnoreCase(body, "credit balance")
        || containsIgnoreCase(body, "billing_error")) {
      return AiResult.keyFailure(AiFailure.QUOTA_EXHAUSTED, detail);
    }
    return switch (status) {
      case 429 -> AiResult.failure(AiFailure.RATE_LIMITED, retryAfter(headers), detail);
      case 401, 403 -> AiResult.keyFailure(AiFailure.AUTH_FAILED, detail);
      case 404 -> AiResult.failure(AiFailure.MODEL_UNAVAILABLE, detail);
      case 400, 413, 422 -> AiResult.failure(AiFailure.BAD_REQUEST, detail);
      // 500, 529 overloaded and anything else unexpected.
      default -> AiResult.failure(AiFailure.TRANSIENT, detail);
    };
  }
}
