package org.smalltech.hashtaglocal_backend.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * Anthropic Messages API client, used for AI-generated quizzes. Like {@link GroqClient}, failures
 * return empty instead of throwing, so the caller can skip the quiz.
 */
@Component
@Slf4j
public class ClaudeClient {

  private final RestClient restClient;
  private final ObjectMapper objectMapper;
  private final String apiKey;
  private final String url;
  private final String model;

  public ClaudeClient(
      RestClient.Builder restClientBuilder,
      ObjectMapper objectMapper,
      @Value("${anthropic.api.key:}") String apiKey,
      @Value("${anthropic.api.url:https://api.anthropic.com/v1/messages}") String url,
      @Value("${anthropic.api.model:claude-sonnet-5-5}") String model) {
    this.restClient = restClientBuilder.build();
    this.objectMapper = objectMapper;
    this.apiKey = apiKey;
    this.url = url;
    this.model = model;
  }

  /** The model's text reply to one user prompt, or empty if unavailable or failed. */
  public Optional<String> complete(String prompt, int maxTokens) {
    if (apiKey.isBlank()) {
      log.warn("ANTHROPIC_API_KEY is not configured; skipping Claude generation");
      return Optional.empty();
    }
    try {
      Map<String, Object> body =
          Map.of(
              "model",
              model,
              "max_tokens",
              maxTokens,
              "messages",
              List.of(Map.of("role", "user", "content", prompt)));
      String raw =
          restClient
              .post()
              .uri(url)
              .header("x-api-key", apiKey)
              .header("anthropic-version", "2023-06-01")
              .contentType(MediaType.APPLICATION_JSON)
              .body(body)
              .retrieve()
              .body(String.class);

      StringBuilder text = new StringBuilder();
      for (JsonNode block : objectMapper.readTree(raw).path("content")) {
        if ("text".equals(block.path("type").asText())) {
          text.append(block.path("text").asText(""));
        }
      }
      return Optional.of(text.toString().trim()).filter(reply -> !reply.isBlank());
    } catch (Exception e) {
      log.warn("Claude completion failed: {}", e.getMessage());
      return Optional.empty();
    }
  }
}
