package org.smalltech.hashtaglocal_backend.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
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

  public static final String QUIZ_GENERATION_TEMPLATE =
      "You are generating a daily quiz question for residents of {{localityName}}, India, in a local"
          + " community app.\n"
          + "\n"
          + "Generate one interesting, factual question about {{localityName}} — its history,"
          + " geography, culture, famous landmarks, local governance, or civic facts. The question"
          + " should be educational and relevant to people who live there.\n"
          + "\n"
          + "{{recentQuestions}}"
          + "Return ONLY a valid JSON object in exactly this format, with no extra text, no markdown,"
          + " no code block:\n"
          + "{\n"
          + "  \"question\": \"...\",\n"
          + "  \"options\": [\"option1\", \"option2\", \"option3\", \"option4\"],\n"
          + "  \"answer_option_index\": 1,\n"
          + "  \"explanation\": \"...\"\n"
          + "}\n"
          + "\n"
          + "Rules:\n"
          + "- question: a clear, specific factual question\n"
          + "- options: exactly 4 strings, one correct and three plausible wrong answers\n"
          + "- answer_option_index: integer 1-4 (1-based index of the correct answer in options)\n"
          + "- explanation: A deeply engaging sentence (1 sentence). It must unpack the \"why\""
          + " behind the answer by sharing incredible local trivia, historical secrets, or a"
          + " surprising context that makes a resident say, \"Wow, I didn't know that about my own"
          + " town!\" Keep the tone witty and enthusiastic. One emoji allowed. Do NOT start with"
          + " \"The answer is\", \"Correct!\", or \"Indeed\".\n"
          + "- All text in English";

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
    String summary = complete(prompt, 200);
    return summary != null ? summary : fallbackSummary(weather);
  }

  /**
   * Structured quiz generation: question + 4 options + answer index + explanation. Returns null on
   * any failure so the weekly job can skip and continue to the next locality/date.
   */
  public record QuizDraft(
      String question, List<String> options, int answerOptionIndex, String explanation) {}

  public QuizDraft generateQuiz(String localityName, List<String> recentQuestions) {
    String recentBlock =
        recentQuestions.isEmpty()
            ? ""
            : "IMPORTANT — Do NOT generate any of these questions that have already been asked"
                + " for this locality:\n"
                + formatRecentQuestions(recentQuestions)
                + "\n\n";
    String prompt =
        QUIZ_GENERATION_TEMPLATE
            .replace("{{localityName}}", localityName)
            .replace("{{recentQuestions}}", recentBlock);
    String raw = complete(prompt, 500);
    if (raw == null) return null;
    try {
      // Strip markdown code fences the model may add
      String json = raw.replaceAll("```(?:json)?", "").trim();
      int start = json.indexOf('{');
      int end = json.lastIndexOf('}');
      if (start == -1 || end == -1) {
        log.warn("Quiz generation response is not JSON for {}: {}", localityName, json);
        return null;
      }
      json = json.substring(start, end + 1);
      JsonNode node = objectMapper.readTree(json);
      String question = node.get("question").asText();
      List<String> options = new ArrayList<>();
      for (JsonNode opt : node.get("options")) {
        options.add(opt.asText());
      }
      int answerOptionIndex = node.get("answer_option_index").asInt();
      String explanation = node.has("explanation") ? node.get("explanation").asText("") : "";
      if (options.size() != 4 || answerOptionIndex < 1 || answerOptionIndex > 4) {
        log.warn("Quiz generation response has invalid structure for {}", localityName);
        return null;
      }
      return new QuizDraft(question, options, answerOptionIndex, explanation);
    } catch (Exception e) {
      log.warn("Failed to parse quiz generation response for {}: {}", localityName, e.getMessage());
      return null;
    }
  }

  private String formatRecentQuestions(List<String> questions) {
    StringBuilder sb = new StringBuilder();
    for (int i = 0; i < questions.size(); i++) {
      sb.append(i + 1).append(". ").append(questions.get(i)).append("\n");
    }
    return sb.toString();
  }

  private String complete(String prompt, int maxTokens) {
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
              maxTokens);
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
