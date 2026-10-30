package org.smalltech.hashtaglocal_backend.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.smalltech.hashtaglocal_backend.service.weather.WeatherSnapshot;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;

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

  // A rate-limited request is retried after Groq's retry-after wait. A wait above the cap means a
  // longer limit (e.g. tokens per day) was hit, so give up rather than hold the caller.
  private static final int MAX_ATTEMPTS = 5;
  private static final long MAX_RETRY_WAIT_SECONDS = 15;
  private static final long DEFAULT_RETRY_WAIT_SECONDS = 5;

  private final RestClient restClient;
  private final ObjectMapper objectMapper;
  private final String apiKey;
  private final String url;
  private final String model;
  private final long requestIntervalMillis;
  private long nextRequestAtMillis;

  /** Weather summary text; {@code fallback} is true for the plain line used when Groq failed. */
  public record WeatherSummary(String text, boolean fallback) {}

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
    String summary = complete(prompt);
    return summary != null
        ? new WeatherSummary(summary, false)
        : new WeatherSummary(fallbackSummary(weather), true);
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
    if (apiKey == null || apiKey.isBlank()) {
      log.warn("GROQ_API_KEY is not configured; skipping Groq generation");
      return null;
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
          return text.isBlank() ? null : text;
        } catch (HttpClientErrorException.TooManyRequests e) {
          long waitSeconds = retryWaitSeconds(e.getResponseHeaders());
          if (attempt >= MAX_ATTEMPTS || waitSeconds > MAX_RETRY_WAIT_SECONDS) {
            throw e;
          }
          log.warn(
              "Groq rate limited (attempt {}/{}); retrying in {}s",
              attempt,
              MAX_ATTEMPTS,
              waitSeconds);
          Thread.sleep(waitSeconds * 1000);
        }
      }
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      log.warn("Groq completion interrupted");
      return null;
    } catch (Exception e) {
      log.warn("Groq completion failed: {}", e.getMessage());
      return null;
    }
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
    String retryAfter = headers != null ? headers.getFirst(HttpHeaders.RETRY_AFTER) : null;
    if (retryAfter != null) {
      try {
        return Math.max(0, Long.parseLong(retryAfter.trim()));
      } catch (NumberFormatException e) {
        log.debug("Unparseable Retry-After header: {}", retryAfter);
      }
    }
    return DEFAULT_RETRY_WAIT_SECONDS;
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
