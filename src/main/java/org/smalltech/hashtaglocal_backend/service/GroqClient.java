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
 */
@Component
@Slf4j
public class GroqClient {

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
   * 1–2 engaging, practical sentences about today's weather (e.g. "Light showers expected this
   * evening — carry an umbrella!"). Falls back to a plain templated line if Groq is unavailable.
   */
  public String generateWeatherSummary(String localityName, WeatherSnapshot weather) {
    String prompt =
        "Write a short, engaging 1-2 sentence weather summary for residents of "
            + localityName
            + ", India. Be practical and friendly (e.g. suggest carrying an umbrella if rain is"
            + " likely). Do not use emojis excessively (at most one). Today's data: "
            + describe(weather)
            + ". Reply with ONLY the summary text, no preamble.";
    String summary = complete(prompt);
    return summary != null ? summary : fallbackSummary(weather);
  }

  /**
   * An engaging, interesting explanation of a quiz answer. Returns an empty string on failure so
   * ops can fill it in manually in the portal.
   */
  public String generateQuizExplanation(
      String question, List<String> options, String correctOption) {
    String prompt =
        "A local community app shows users a daily quiz. Write a short, engaging and interesting"
            + " explanation (2-3 sentences) of the correct answer, including a fun or useful fact"
            + " if possible.\nQuestion: "
            + question
            + "\nOptions: "
            + String.join(" | ", options)
            + "\nCorrect answer: "
            + correctOption
            + "\nReply with ONLY the explanation text, no preamble.";
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
