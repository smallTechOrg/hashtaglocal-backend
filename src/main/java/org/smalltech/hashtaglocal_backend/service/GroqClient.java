package org.smalltech.hashtaglocal_backend.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.smalltech.hashtaglocal_backend.service.weather.WeatherSnapshot;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * Groq chat-completions client (OpenAI-compatible REST, no SDK) for the two bulletin texts: the
 * engaging weather summary and the quiz explanation. Both callers treat a failure as non-fatal —
 * methods return a fallback/empty string rather than throwing, so the daily job and admin quiz save
 * never block on Groq.
 *
 * <p>Prompt templates use {@code {{placeholder}}} syntax. The ops portal exposes them read-only via
 * {@code GET /admin/ai-prompts} so the team can review what is being sent to the model.
 */
@Component
@Slf4j
public class GroqClient {

  // ── Prompt templates ({{placeholder}} replaced at call time) ─────────────

  public static final String WEATHER_SUMMARY_TEMPLATE =
      "Role: You are a helpful weather assistant writing localized daily advice.\n"
          + "\n"
          + "Task: Write exactly one short, practical sentence of advice based on the provided"
          + " weather data.\n"
          + "\n"
          + "Rules:\n"
          + "1. Do NOT start the sentence with the city name or phrases like \"Residents of...\".\n"
          + "2. Do NOT mention or repeat raw numbers (e.g., temperature, humidity, rain chance)"
          + " because the user can already see them on screen.\n"
          + "3. Focus entirely on actionable advice — what the person should DO or WATCH OUT FOR"
          + " based on how the day feels.\n"
          + "4. Keep the tone direct and friendly.\n"
          + "5. Use at most one emoji.\n"
          + "6. Reply with ONLY the sentence. No preamble, no intro, no conversational filler.\n"
          + "\n"
          + "Thresholds & Logic:\n"
          + "- If rain probability is low (under 50%), treat it as a fine, standard day. Do NOT"
          + " give precautionary warnings like \"carry an umbrella\" or \"watch out for rain.\""
          + " Instead, give routine advice for a pleasant day (e.g., enjoying the weather, staying"
          + " comfortable).\n"
          + "- Only advise carrying an umbrella or preparing for wet weather if the rain probability"
          + " is 50% or higher.\n"
          + "\n"
          + "{{weatherData}}, Location: {{localityName}}, India.";

  public static final String QUIZ_EXPLANATION_TEMPLATE =
      "Write 1-2 short sentences explaining why this answer is correct for a local community quiz."
          + " Weave in one interesting or useful fact if it fits naturally — skip it if it doesn't."
          + " Keep it simple; no jargon.\n"
          + "Question: {{question}}\n"
          + "Correct answer: {{correctOption}}\n"
          + "Reply with ONLY the explanation, no preamble.";

  // ─────────────────────────────────────────────────────────────────────────

  private final RestClient restClient;
  private final ObjectMapper objectMapper;
  private final String apiKey;
  private final String url;
  private final String model;

  public GroqClient(
      RestClient.Builder restClientBuilder,
      ObjectMapper objectMapper,
      @Value("${groq.api.key:}") String apiKey,
      @Value("${groq.api.url:https://api.groq.com/openai/v1/chat/completions}") String url,
      @Value("${groq.api.model:llama-3.3-70b-versatile}") String model) {
    this.restClient = restClientBuilder.build();
    this.objectMapper = objectMapper;
    this.apiKey = apiKey;
    this.url = url;
    this.model = model;
  }

  /**
   * One punchy, actionable sentence about today's weather. Falls back to a plain templated line if
   * Groq is unavailable.
   */
  public String generateWeatherSummary(String localityName, WeatherSnapshot weather) {
    String prompt =
        WEATHER_SUMMARY_TEMPLATE
            .replace("{{localityName}}", localityName)
            .replace("{{weatherData}}", describe(weather));
    String summary = complete(prompt);
    return summary != null ? summary : fallbackSummary(weather);
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
    } catch (Exception e) {
      log.warn("Groq completion failed: {}", e.getMessage());
      return null;
    }
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
