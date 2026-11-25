package org.smalltech.hashtaglocal_backend.service.ai;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

/**
 * Calls the AI connection saved in the Ops Dashboard. Every connection uses the OpenAI Chat
 * Completions format, which Claude, ChatGPT, Gemini, OpenRouter, DeepSeek, Groq and most others
 * also accept, e.g. {@code https://api.anthropic.com/v1}, {@code https://api.openai.com/v1}, {@code
 * https://generativelanguage.googleapis.com/v1beta/openai}.
 *
 * <ul>
 *   <li>A base URL ({@code https://host/v1}) gets {@code /chat/completions} appended; a full URL is
 *       used as is.
 *   <li>Sends {@code max_tokens}. If the server asks for {@code max_completion_tokens} instead (as
 *       OpenAI's reasoning models do), retries once with that.
 *   <li>Strips {@code <think>...</think>} blocks that reasoning models put before the answer, so
 *       the quiz parser sees only the answer.
 * </ul>
 *
 * <p>Never throws: every failure comes back as an {@link AiResult}.
 */
@Component
public class OpenAiCompatibleClient {

  static final String CHAT_PATH = "/chat/completions";

  private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(10);
  private static final Duration READ_TIMEOUT = Duration.ofSeconds(60);
  private static final Pattern THINK_BLOCK = Pattern.compile("(?s)<think>.*?</think>");

  private final RestClient restClient;
  private final ObjectMapper objectMapper;

  public OpenAiCompatibleClient(RestClient.Builder restClientBuilder, ObjectMapper objectMapper) {
    SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
    requestFactory.setConnectTimeout(CONNECT_TIMEOUT);
    requestFactory.setReadTimeout(READ_TIMEOUT);
    this.restClient = restClientBuilder.clone().requestFactory(requestFactory).build();
    this.objectMapper = objectMapper;
  }

  /** Sends one user prompt to {@code model} at {@code endpoint} using {@code apiKey}. */
  public AiResult call(String endpoint, String model, String apiKey, String prompt, int maxTokens) {
    if (endpoint == null || endpoint.isBlank()) {
      return AiResult.failure(AiFailure.BAD_REQUEST, "no endpoint configured");
    }
    String url = chatUrl(endpoint);
    String raw;
    try {
      raw = send(url, apiKey, body(model, prompt, maxTokens, "max_tokens"));
    } catch (RestClientResponseException e) {
      String errorBody = e.getResponseBodyAsString();
      if (e.getStatusCode().value() == 400
          && RestAiProvider.containsIgnoreCase(errorBody, "max_completion_tokens")) {
        try {
          raw = send(url, apiKey, body(model, prompt, maxTokens, "max_completion_tokens"));
        } catch (RestClientResponseException retry) {
          return classify(
              retry.getStatusCode().value(),
              retry.getResponseHeaders(),
              retry.getResponseBodyAsString());
        } catch (RestClientException retry) {
          return transientFailure(retry);
        }
      } else {
        return classify(e.getStatusCode().value(), e.getResponseHeaders(), errorBody);
      }
    } catch (RestClientException e) {
      return transientFailure(e);
    }
    try {
      String text = extractText(raw);
      return text == null || text.isBlank()
          ? AiResult.failure(AiFailure.INVALID_RESPONSE, "reply had no text")
          : AiResult.success(text.trim());
    } catch (Exception e) {
      return AiResult.failure(
          AiFailure.INVALID_RESPONSE,
          "unreadable reply: " + RestAiProvider.shorten(SecretRedactor.redact(e.getMessage())));
    }
  }

  /** Sends the request; the key goes in a header, never in the URL. */
  protected String send(String url, String apiKey, Map<String, Object> body) {
    return restClient
        .post()
        .uri(url)
        .header(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey)
        .contentType(MediaType.APPLICATION_JSON)
        .body(body)
        .retrieve()
        .body(String.class);
  }

  /** The request body, with the token limit under {@code tokenField}. */
  static Map<String, Object> body(String model, String prompt, int maxTokens, String tokenField) {
    return Map.of(
        "model",
        model,
        "messages",
        List.of(Map.of("role", "user", "content", prompt)),
        tokenField,
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

  String extractText(String raw) throws Exception {
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

  AiResult classify(int status, HttpHeaders headers, String body) {
    String detail = RestAiProvider.detail(status, body);
    if (status == 402
        || RestAiProvider.containsIgnoreCase(body, "insufficient_quota")
        || RestAiProvider.containsIgnoreCase(body, "insufficient credits")
        || RestAiProvider.containsIgnoreCase(body, "credit balance")) {
      return AiResult.keyFailure(AiFailure.QUOTA_EXHAUSTED, detail);
    }
    return switch (status) {
      case 429 ->
          AiResult.failure(AiFailure.RATE_LIMITED, RestAiProvider.retryAfter(headers), detail);
      case 401, 403 -> AiResult.keyFailure(AiFailure.AUTH_FAILED, detail);
      // Unknown model, or a wrong endpoint path; the detail shows which.
      case 404 -> AiResult.failure(AiFailure.MODEL_UNAVAILABLE, detail);
      case 400, 413, 422 ->
          RestAiProvider.containsIgnoreCase(body, "model_not_found")
                  || RestAiProvider.containsIgnoreCase(body, "model not found")
                  || RestAiProvider.containsIgnoreCase(body, "does not exist")
              ? AiResult.failure(AiFailure.MODEL_UNAVAILABLE, detail)
              : AiResult.failure(AiFailure.BAD_REQUEST, detail);
      default -> AiResult.failure(AiFailure.TRANSIENT, detail);
    };
  }

  private static AiResult transientFailure(RestClientException e) {
    // Timeouts and connection errors.
    return AiResult.failure(
        AiFailure.TRANSIENT, RestAiProvider.shorten(SecretRedactor.redact(e.getMessage())));
  }
}
