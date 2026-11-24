package org.smalltech.hashtaglocal_backend.service.ai;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.http.HttpHeaders;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

/**
 * Shared plumbing for the REST providers: send the request, turn HTTP errors into {@link
 * AiFailure}s, and pull the text out of a good reply. Subclasses supply only the API specifics.
 */
abstract class RestAiProvider implements AiProvider {

  private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(10);
  // A hung call would stop the chain from ever falling back, so every call has a deadline.
  private static final Duration READ_TIMEOUT = Duration.ofSeconds(60);
  private static final int MAX_DETAIL_CHARS = 200;
  // All three providers put a readable "message" inside their JSON error body.
  private static final Pattern ERROR_MESSAGE =
      Pattern.compile("\"message\"\\s*:\\s*\"((?:[^\"\\\\]|\\\\.)*)\"");

  protected final RestClient restClient;
  protected final ObjectMapper objectMapper;

  protected RestAiProvider(RestClient.Builder restClientBuilder, ObjectMapper objectMapper) {
    SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
    requestFactory.setConnectTimeout(CONNECT_TIMEOUT);
    requestFactory.setReadTimeout(READ_TIMEOUT);
    this.restClient = restClientBuilder.clone().requestFactory(requestFactory).build();
    this.objectMapper = objectMapper;
  }

  @Override
  public final AiResult call(
      String endpoint, String model, String apiKey, String prompt, int maxTokens) {
    String url = endpoint == null || endpoint.isBlank() ? defaultEndpoint() : endpoint.trim();
    if (url == null || url.isBlank()) {
      // A provider with no default (openai-compatible) needs an endpoint.
      return AiResult.failure(AiFailure.BAD_REQUEST, "no endpoint configured for " + name());
    }
    String raw;
    try {
      raw = send(url, model, apiKey, prompt, maxTokens);
    } catch (RestClientResponseException e) {
      return classify(
          e.getStatusCode().value(), e.getResponseHeaders(), e.getResponseBodyAsString());
    } catch (RestClientException e) {
      // Timeouts and connection errors.
      return AiResult.failure(AiFailure.TRANSIENT, shorten(SecretRedactor.redact(e.getMessage())));
    }
    try {
      String text = extractText(raw);
      return text == null || text.isBlank()
          ? AiResult.failure(AiFailure.INVALID_RESPONSE, "reply had no text")
          : AiResult.success(text.trim());
    } catch (Exception e) {
      return AiResult.failure(
          AiFailure.INVALID_RESPONSE,
          "unreadable reply: " + shorten(SecretRedactor.redact(e.getMessage())));
    }
  }

  /**
   * Sends the request to {@code url} and returns the raw response body. HTTP errors are thrown by
   * RestClient. Implementations must send the key in a header, never in the URL.
   */
  protected abstract String send(
      String url, String model, String apiKey, String prompt, int maxTokens);

  /** The reply text from a successful response body. */
  protected abstract String extractText(String raw) throws Exception;

  /** Maps an HTTP error to a failure type. */
  abstract AiResult classify(int status, HttpHeaders headers, String body);

  /** The Retry-After header in seconds (rounded up), or null when absent or unreadable. */
  static Duration retryAfter(HttpHeaders headers) {
    String value = headers == null ? null : headers.getFirst(HttpHeaders.RETRY_AFTER);
    if (value == null) {
      return null;
    }
    try {
      return Duration.ofSeconds((long) Math.ceil(Double.parseDouble(value.trim())));
    } catch (NumberFormatException e) {
      return null;
    }
  }

  /**
   * HTTP status plus the provider's error message (or the start of the body), for logs and for the
   * Ops Dashboard. Some providers echo part of the key back (e.g. "Incorrect API key provided:
   * sk-ab***yz"), so anything key-shaped is masked.
   */
  static String detail(int status, String body) {
    Matcher message = ERROR_MESSAGE.matcher(body == null ? "" : body);
    return status + " " + shorten(SecretRedactor.redact(message.find() ? message.group(1) : body));
  }

  private static String shorten(String text) {
    if (text == null) {
      return "";
    }
    String oneLine = text.replaceAll("\\s+", " ").trim();
    return oneLine.length() <= MAX_DETAIL_CHARS
        ? oneLine
        : oneLine.substring(0, MAX_DETAIL_CHARS) + "...";
  }

  static boolean containsIgnoreCase(String body, String fragment) {
    return body != null
        && body.toLowerCase(Locale.ROOT).contains(fragment.toLowerCase(Locale.ROOT));
  }
}
