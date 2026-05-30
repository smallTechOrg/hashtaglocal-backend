package org.smalltech.hashtaglocal_backend.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.smalltech.hashtaglocal_backend.model.AiCategory;
import org.smalltech.hashtaglocal_backend.model.AiVerdict;
import org.smalltech.hashtaglocal_backend.service.FeedModerationClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * AI moderation via the Gemini REST API (no SDK). Requests structured JSON output so the verdict
 * parses deterministically. See FEED_DESIGN.md §8.
 */
@Component
@Slf4j
public class GeminiFeedModerationClient implements FeedModerationClient {

  private static final String RUBRIC =
      "You are a content moderator for a public neighbourhood community feed. Classify the post"
          + " into exactly one category and decide whether to ALLOW, BLOCK, or mark UNCERTAIN."
          + " Categories: NONE (clean), SPAM, HATE, NSFW, HARASSMENT, VIOLENCE, MISINFORMATION,"
          + " OFF_TOPIC, OTHER. BLOCK clear violations; ALLOW clean civic/community content; use"
          + " UNCERTAIN only when genuinely ambiguous. Respond with the JSON schema provided.";

  private final RestClient restClient;
  private final ObjectMapper objectMapper;
  private final String apiKey;
  private final String model;
  private final String endpoint;

  public GeminiFeedModerationClient(
      RestClient.Builder restClientBuilder,
      ObjectMapper objectMapper,
      @Value("${feed.moderation.gemini-api-key:${GEMINI_API_KEY:}}") String apiKey,
      @Value("${feed.moderation.model:gemini-2.0-flash}") String model,
      @Value("${feed.moderation.endpoint:https://generativelanguage.googleapis.com/v1beta/models}")
          String endpoint) {
    this.restClient = restClientBuilder.build();
    this.objectMapper = objectMapper;
    this.apiKey = apiKey;
    this.model = model;
    this.endpoint = endpoint;
  }

  @Override
  public ModerationResult classify(String text, String linkUrl, String linkTitle) {
    if (apiKey == null || apiKey.isBlank()) {
      throw new IllegalStateException("GEMINI_API_KEY is not configured");
    }

    String content =
        "POST TEXT:\n"
            + (text == null ? "" : text)
            + "\n\nLINK URL: "
            + (linkUrl == null ? "(none)" : linkUrl)
            + "\nLINK TITLE: "
            + (linkTitle == null ? "(none)" : linkTitle);

    Map<String, Object> requestBody =
        Map.of(
            "system_instruction", Map.of("parts", List.of(Map.of("text", RUBRIC))),
            "contents", List.of(Map.of("parts", List.of(Map.of("text", content)))),
            "generationConfig",
                Map.of(
                    "responseMimeType",
                    "application/json",
                    "responseSchema",
                    responseSchema(),
                    "temperature",
                    0));

    String url = endpoint + "/" + model + ":generateContent?key=" + apiKey;

    String raw = restClient.post().uri(url).body(requestBody).retrieve().body(String.class);

    return parse(raw);
  }

  private Map<String, Object> responseSchema() {
    return Map.of(
        "type", "OBJECT",
        "properties",
            Map.of(
                "verdict", Map.of("type", "STRING", "enum", List.of("ALLOW", "BLOCK", "UNCERTAIN")),
                "category",
                    Map.of(
                        "type",
                        "STRING",
                        "enum",
                        List.of(
                            "NONE",
                            "SPAM",
                            "HATE",
                            "NSFW",
                            "HARASSMENT",
                            "VIOLENCE",
                            "MISINFORMATION",
                            "OFF_TOPIC",
                            "OTHER")),
                "confidence", Map.of("type", "NUMBER"),
                "reason", Map.of("type", "STRING")),
        "required", List.of("verdict", "category", "confidence", "reason"));
  }

  private ModerationResult parse(String raw) {
    try {
      JsonNode root = objectMapper.readTree(raw);
      // Gemini returns the model output as text inside candidates[0].content.parts[0].text
      JsonNode textNode =
          root.path("candidates").path(0).path("content").path("parts").path(0).path("text");
      JsonNode verdictJson = objectMapper.readTree(textNode.asText());

      AiVerdict verdict =
          AiVerdict.valueOf(
              verdictJson.path("verdict").asText("UNCERTAIN").toUpperCase(Locale.ROOT));
      AiCategory category =
          AiCategory.valueOf(verdictJson.path("category").asText("OTHER").toUpperCase(Locale.ROOT));
      double confidence = verdictJson.path("confidence").asDouble(0.0);
      String reason = verdictJson.path("reason").asText("");
      return new ModerationResult(verdict, category, confidence, reason, model);
    } catch (Exception e) {
      throw new IllegalStateException("Failed to parse Gemini moderation response", e);
    }
  }
}
