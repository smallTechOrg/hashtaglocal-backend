package org.smalltech.hashtaglocal_backend.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;
import lombok.extern.slf4j.Slf4j;
import org.smalltech.hashtaglocal_backend.service.weather.WeatherSnapshot;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * Groq chat-completions client (OpenAI-compatible REST, no SDK) for the two bulletin texts: the
 * engaging weather summary and the quiz explanation. Both callers treat a failure as non-fatal —
 * methods return a fallback/empty string rather than throwing, so the daily job and admin quiz save
 * never block on Groq.
 *
 * <p>Requests are spaced to {@code groq.api.requests-per-minute} across all callers, and a 429 that
 * still gets through is retried after the wait Groq asks for.
 *
 * <p>Prompt templates use {@code {{placeholder}}} syntax. The ops portal exposes them read-only via
 * {@code GET /admin/ai-prompts} so the team can review what is being sent to the model.
 */
@Component
@Slf4j
public class GroqClient {

  // ── Prompt templates ({{placeholder}} replaced at call time) ─────────────

  public static final String WEATHER_SUMMARY_TEMPLATE =
      "You are a witty, hyper-local weather companion. Write exactly ONE punchy sentence of"
          + " daily advice for residents of {{localityName}}, India.\n"
          + "\n"
          + "Weather data: {{weatherData}}\n"
          + "\n"
          + "{{recentSummaries}}"
          + "BANNED openers — starting with any of these makes the response invalid:\n"
          + "\"Be sure to\", \"Make sure to\", \"Stay hydrated\", \"Keep yourself\","
          + " \"Residents of\", \"It's going to be\", \"Today is going to be\","
          + " \"The weather today\", \"With temperatures\".\n"
          + "\n"
          + "BANNED phrases (do not use anywhere in the sentence):\n"
          + "\"carry an umbrella\" (say something more specific like \"grab a raincoat\","
          + " \"brace for downpours\", \"watch for waterlogging\", \"skip the two-wheeler\").\n"
          + "\n"
          + "Rules:\n"
          + "1. Do NOT mention raw numbers — the user already sees temp, humidity, and rain % on"
          + " screen.\n"
          + "2. Advice must feel specific to TODAY's conditions, not a generic all-weather tip.\n"
          + "3. Rain ≥ 50%: warn about wet conditions using varied, vivid language. Rain < 50%:"
          + " zero rain warnings — focus on heat, humidity, breeze, or how the day feels.\n"
          + "4. One emoji allowed (optional). Tone: direct, friendly, occasionally witty.\n"
          + "5. Reply with ONLY the sentence. No preamble, no filler.";

  public static final String QUIZ_EXPLANATION_TEMPLATE =
      "Write 1-2 short sentences explaining why this answer is correct for a local community quiz."
          + " Weave in one interesting or useful fact if it fits naturally — skip it if it doesn't."
          + " Keep it simple; no jargon.\n"
          + "Question: {{question}}\n"
          + "Correct answer: {{correctOption}}\n"
          + "Reply with ONLY the explanation, no preamble.";

  // ─────────────────────────────────────────────────────────────────────────

  // A rate-limited (429) request is retried after Groq's retry-after wait. A transient failure
  // (503 "over capacity", other 5xx gateway errors, timeouts) is retried with exponential backoff,
  // or after Groq's retry-after wait when it sends one. A wait above the cap means a longer limit
  // (e.g. tokens per day) was hit, so give up rather than hold the caller.
  private static final int MAX_ATTEMPTS = 5;
  private static final long MAX_RETRY_WAIT_SECONDS = 15;
  private static final long DEFAULT_RETRY_WAIT_SECONDS = 5;
  private static final long DEFAULT_BACKOFF_BASE_MILLIS = 2_000;
  private static final Set<Integer> TRANSIENT_SERVER_STATUSES = Set.of(500, 502, 503, 504);
  private static final int MAX_FAILURE_REASON_LENGTH = 200;

  private final RestClient restClient;
  private final ObjectMapper objectMapper;
  private final String apiKey;
  private final String url;
  private final String model;
  private final long requestIntervalMillis;
  private long nextRequestAtMillis;
  private long backoffBaseMillis = DEFAULT_BACKOFF_BASE_MILLIS;

  /**
   * Weather summary text; {@code fallback} is true for the plain line used when Groq failed, in
   * which case {@code failureReason} says why (null otherwise).
   */
  public record WeatherSummary(String text, boolean fallback, String failureReason) {
    public WeatherSummary(String text, boolean fallback) {
      this(text, fallback, null);
    }
  }

  /** Result of one completion: the text, or null text with the reason it could not be had. */
  private record Completion(String text, String failureReason) {}

  public GroqClient(
      RestClient.Builder restClientBuilder,
      ObjectMapper objectMapper,
      @Value("${groq.api.key:}") String apiKey,
      @Value("${groq.api.url:https://api.groq.com/openai/v1/chat/completions}") String url,
      @Value("${groq.api.model:qwen/qwen3.8-27b}") String model,
      @Value("${groq.api.requests-per-minute:12}") int requestsPerMinute) {
    this.restClient = restClientBuilder.build();
    this.objectMapper = objectMapper;
    this.apiKey = apiKey;
    this.url = url;
    this.model = model;
    this.requestIntervalMillis = requestsPerMinute > 0 ? 60_000L / requestsPerMinute : 0;
  }

  /**
   * One punchy, actionable sentence about today's weather. Falls back to a plain templated line if
   * Groq is unavailable. Pass the last N summaries for this locality so the model avoids repeating
   * them when conditions are similar across consecutive days.
   */
  public WeatherSummary generateWeatherSummary(
      String localityName, WeatherSnapshot weather, List<String> recentSummaries) {
    String prompt =
        WEATHER_SUMMARY_TEMPLATE
            .replace("{{localityName}}", localityName)
            .replace("{{weatherData}}", describe(weather))
            .replace("{{recentSummaries}}", formatRecentSummaries(recentSummaries));
    Completion completion = completeWithReason(prompt);
    return completion.text() != null
        ? new WeatherSummary(completion.text(), false, null)
        : new WeatherSummary(fallbackSummary(weather), true, completion.failureReason());
  }

  private String formatRecentSummaries(List<String> summaries) {
    if (summaries == null || summaries.isEmpty()) return "";
    StringBuilder sb =
        new StringBuilder(
            "RECENT SUMMARIES FOR THIS LOCALITY — do NOT repeat or closely paraphrase any of"
                + " these:\n");
    for (int i = 0; i < summaries.size(); i++) {
      sb.append(i + 1).append(". ").append(summaries.get(i)).append("\n");
    }
    sb.append("\n");
    return sb.toString();
  }

  /**
   * 1–2 short sentences explaining the correct quiz answer. Returns an empty string on failure so
   * ops can fill it in manually in the portal.
   */
  public String generateQuizExplanation(String question, String correctOption) {
    String prompt =
        QUIZ_EXPLANATION_TEMPLATE
            .replace("{{question}}", question)
            .replace("{{correctOption}}", correctOption);
    String explanation = complete(prompt);
    return explanation != null ? explanation : "";
  }

  private String complete(String prompt) {
    return completeWithReason(prompt).text();
  }

  private Completion completeWithReason(String prompt) {
    if (apiKey == null || apiKey.isBlank()) {
      log.warn("GROQ_API_KEY is not configured; skipping Groq generation");
      return new Completion(null, "GROQ_API_KEY is not configured");
    }
    try {
      Map<String, Object> body =
          Map.of(
              "model",
              model,
              "messages",
              List.of(Map.of("role", "user", "content", prompt)),
              "temperature",
              0.7,
              "max_tokens",
              200);
      for (int attempt = 1; ; attempt++) {
        awaitRequestSlot();
        try {
          String raw =
              restClient
                  .post()
                  .uri(url)
                  .header("Authorization", "Bearer " + apiKey)
                  .contentType(MediaType.APPLICATION_JSON)
                  .body(body)
                  .retrieve()
                  .body(String.class);
          JsonNode content =
              objectMapper.readTree(raw).path("choices").path(0).path("message").path("content");
          String text = content.asText("").trim();
          return text.isBlank()
              ? new Completion(null, "Groq returned an empty response")
              : new Completion(text, null);
        } catch (RestClientException e) {
          long delayMillis = retryDelayMillis(e, attempt);
          if (delayMillis < 0) {
            throw e;
          }
          log.warn(
              "Groq request failed (attempt {}/{}): {}; retrying in {}ms",
              attempt,
              MAX_ATTEMPTS,
              e.getMessage(),
              delayMillis);
          Thread.sleep(delayMillis);
        }
      }
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      log.warn("Groq completion interrupted");
      return new Completion(null, "Groq request was interrupted");
    } catch (Exception e) {
      log.warn("Groq completion failed: {}", e.getMessage());
      return new Completion(null, describeFailure(e));
    }
  }

  /**
   * How long to wait before the next attempt, or -1 to give up (out of attempts, a wait above the
   * cap, or an error retrying cannot fix such as 401/400).
   */
  private long retryDelayMillis(RestClientException e, int attempt) {
    if (attempt >= MAX_ATTEMPTS) {
      return -1;
    }
    if (e instanceof HttpClientErrorException.TooManyRequests tooMany) {
      return capped(retryWaitSeconds(tooMany.getResponseHeaders()));
    }
    if (e instanceof HttpServerErrorException serverError) {
      if (!TRANSIENT_SERVER_STATUSES.contains(serverError.getStatusCode().value())) {
        return -1;
      }
      Long retryAfter = retryAfterSeconds(serverError.getResponseHeaders());
      return retryAfter != null ? capped(retryAfter) : backoffMillis(attempt);
    }
    if (e instanceof ResourceAccessException) { // connect / read timeout, I/O error
      return backoffMillis(attempt);
    }
    return -1;
  }

  private static long capped(long waitSeconds) {
    return waitSeconds > MAX_RETRY_WAIT_SECONDS ? -1 : waitSeconds * 1000;
  }

  /** Exponential backoff (base, 2x, 4x, ...) up to the wait cap, plus up to 25% random jitter. */
  long backoffMillis(int attempt) {
    long delay = Math.min(backoffBaseMillis << (attempt - 1), MAX_RETRY_WAIT_SECONDS * 1000);
    return delay + ThreadLocalRandom.current().nextLong(delay / 4 + 1);
  }

  /** Test hook: shrinks the backoff so retry tests do not really wait seconds. */
  void setBackoffBaseMillis(long backoffBaseMillis) {
    this.backoffBaseMillis = backoffBaseMillis;
  }

  /** Short, single-line reason for the ops alert email. Never includes the API key. */
  static String describeFailure(Exception e) {
    String message = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
    message = message.replaceAll("\\s+", " ").trim();
    return message.length() > MAX_FAILURE_REASON_LENGTH
        ? message.substring(0, MAX_FAILURE_REASON_LENGTH) + "..."
        : message;
  }

  /**
   * Waits for this request's turn; the slot is reserved under the lock, the sleep is outside it.
   */
  private void awaitRequestSlot() throws InterruptedException {
    long delayMillis = reserveRequestSlot();
    if (delayMillis > 0) {
      Thread.sleep(delayMillis);
    }
  }

  private synchronized long reserveRequestSlot() {
    long now = System.currentTimeMillis();
    long slot = Math.max(now, nextRequestAtMillis);
    nextRequestAtMillis = slot + requestIntervalMillis;
    return slot - now;
  }

  static long retryWaitSeconds(HttpHeaders headers) {
    Long retryAfter = retryAfterSeconds(headers);
    return retryAfter != null ? retryAfter : DEFAULT_RETRY_WAIT_SECONDS;
  }

  /** Seconds from a numeric Retry-After header, or null when absent or unparseable. */
  private static Long retryAfterSeconds(HttpHeaders headers) {
    String retryAfter = headers != null ? headers.getFirst(HttpHeaders.RETRY_AFTER) : null;
    if (retryAfter != null) {
      try {
        return Math.max(0, Long.parseLong(retryAfter.trim()));
      } catch (NumberFormatException e) {
        log.debug("Unparseable Retry-After header: {}", retryAfter);
      }
    }
    return null;
  }

  private String fallbackSummary(WeatherSnapshot w) {
    StringBuilder sb = new StringBuilder("Today: ");
    if (w.getMinTemp() != null && w.getMaxTemp() != null) {
      sb.append(Math.round(w.getMinTemp()))
          .append("°C to ")
          .append(Math.round(w.getMaxTemp()))
          .append("°C");
    }
    if (w.getRainProbability() != null) {
      sb.append(", ").append(Math.round(w.getRainProbability())).append("% chance of rain");
    }
    sb.append(".");
    return sb.toString();
  }

  private String describe(WeatherSnapshot w) {
    return "min temp "
        + w.getMinTemp()
        + "°C, max temp "
        + w.getMaxTemp()
        + "°C, humidity "
        + w.getHumidity()
        + "%, rain probability "
        + w.getRainProbability()
        + "%"
        + (w.getAvgAqi() != null ? ", AQI " + w.getAvgAqi() : "");
  }
}
