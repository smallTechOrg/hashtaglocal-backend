package org.smalltech.hashtaglocal_backend.service;

import java.time.Duration;
import java.util.List;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.smalltech.hashtaglocal_backend.service.ai.AiFailure;
import org.smalltech.hashtaglocal_backend.service.ai.AiFallbackChain;
import org.smalltech.hashtaglocal_backend.service.ai.AiProviderException;
import org.smalltech.hashtaglocal_backend.service.weather.WeatherSnapshot;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Writes the two bulletin texts, the engaging weather summary and the quiz explanation, with the AI
 * provider saved in the Ops Dashboard (through {@link AiFallbackChain}). Both callers treat a
 * failure as non-fatal: methods return a fallback/empty string rather than throwing, so the daily
 * job and admin quiz save never block on the AI.
 *
 * <p>Requests are spaced to {@code bulletin.ai.requests-per-minute} across all callers, and a rate
 * limit that still gets through is retried a few times after a short wait.
 *
 * <p>Prompt templates use {@code {{placeholder}}} syntax. The ops portal exposes them read-only via
 * {@code GET /admin/ai-prompts} so the team can review what is being sent to the model.
 */
@Component
@Slf4j
public class BulletinAiClient {

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

  private static final int MAX_TOKENS = 200;
  private static final int MAX_ATTEMPTS = 3;
  private static final Duration DEFAULT_RETRY_WAIT = Duration.ofSeconds(5);

  private final AiFallbackChain ai;
  private final long requestIntervalMillis;
  private final Duration retryWait;
  private long nextRequestAtMillis;

  /** Weather summary text; {@code fallback} is true for the plain line used when the AI failed. */
  public record WeatherSummary(String text, boolean fallback) {}

  @Autowired
  public BulletinAiClient(
      AiFallbackChain ai, @Value("${bulletin.ai.requests-per-minute:12}") int requestsPerMinute) {
    this(ai, requestsPerMinute, DEFAULT_RETRY_WAIT);
  }

  BulletinAiClient(AiFallbackChain ai, int requestsPerMinute, Duration retryWait) {
    this.ai = ai;
    this.requestIntervalMillis = requestsPerMinute > 0 ? 60_000L / requestsPerMinute : 0;
    this.retryWait = retryWait;
  }

  /**
   * One punchy, actionable sentence about today's weather. Falls back to a plain templated line if
   * the AI is unavailable. Pass the last N summaries for this locality so the model avoids
   * repeating them when conditions are similar across consecutive days.
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

  /** The AI's reply, or null when it failed or had no text. Never throws. */
  private String complete(String prompt) {
    try {
      for (int attempt = 1; ; attempt++) {
        awaitRequestSlot();
        try {
          Optional<AiFallbackChain.AiCompletion> reply = ai.complete(prompt, MAX_TOKENS);
          String text = reply.map(AiFallbackChain.AiCompletion::text).orElse("").trim();
          return text.isBlank() ? null : text;
        } catch (AiProviderException e) {
          if (e.getFailure() != AiFailure.RATE_LIMITED || attempt >= MAX_ATTEMPTS) {
            throw e;
          }
          log.warn(
              "Bulletin AI rate limited (attempt {}/{}); retrying in {}s",
              attempt,
              MAX_ATTEMPTS,
              retryWait.toSeconds());
          Thread.sleep(retryWait.toMillis());
        }
      }
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      log.warn("Bulletin AI completion interrupted");
      return null;
    } catch (Exception e) {
      log.warn("Bulletin AI completion failed: {}", e.getMessage());
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
