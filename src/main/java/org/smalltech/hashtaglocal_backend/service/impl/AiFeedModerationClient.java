package org.smalltech.hashtaglocal_backend.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Locale;
import org.smalltech.hashtaglocal_backend.model.AiCategory;
import org.smalltech.hashtaglocal_backend.model.AiVerdict;
import org.smalltech.hashtaglocal_backend.service.FeedModerationClient;
import org.smalltech.hashtaglocal_backend.service.ai.AiFallbackChain;
import org.springframework.stereotype.Component;

/**
 * AI moderation with the AI provider saved in the Ops Dashboard (through {@link AiFallbackChain}).
 * The model is asked for a JSON verdict; anything it cannot parse throws, so the caller flags the
 * post for human review. See FEED_DESIGN.md §8.
 */
@Component
public class AiFeedModerationClient implements FeedModerationClient {

  static final String RUBRIC =
      "You are a content moderator for a public neighbourhood community feed. Classify the post"
          + " into exactly one category and decide whether to ALLOW, BLOCK, or mark UNCERTAIN."
          + " Categories: NONE (clean), SPAM, HATE, NSFW, HARASSMENT, VIOLENCE, MISINFORMATION,"
          + " OFF_TOPIC, OTHER. BLOCK clear violations; ALLOW clean civic/community content; use"
          + " UNCERTAIN only when genuinely ambiguous.\n"
          + "\n"
          + "Reply with ONLY a JSON object, no markdown and no other text:\n"
          + "{\"verdict\": \"ALLOW|BLOCK|UNCERTAIN\", \"category\": \"NONE|SPAM|HATE|NSFW|"
          + "HARASSMENT|VIOLENCE|MISINFORMATION|OFF_TOPIC|OTHER\", \"confidence\": <0.0-1.0>,"
          + " \"reason\": \"<one short sentence>\"}";

  private static final int MAX_TOKENS = 300;

  private final AiFallbackChain ai;
  private final ObjectMapper objectMapper;

  public AiFeedModerationClient(AiFallbackChain ai, ObjectMapper objectMapper) {
    this.ai = ai;
    this.objectMapper = objectMapper;
  }

  @Override
  public ModerationResult classify(String text, String linkUrl, String linkTitle) {
    String prompt =
        RUBRIC
            + "\n\nPOST TEXT:\n"
            + (text == null ? "" : text)
            + "\n\nLINK URL: "
            + (linkUrl == null ? "(none)" : linkUrl)
            + "\nLINK TITLE: "
            + (linkTitle == null ? "(none)" : linkTitle);

    AiFallbackChain.AiCompletion reply =
        ai.complete(prompt, MAX_TOKENS)
            .orElseThrow(() -> new IllegalStateException("AI moderation returned no reply"));
    return parse(reply.text(), reply.generatedBy());
  }

  ModerationResult parse(String reply, String model) {
    try {
      JsonNode verdictJson = objectMapper.readTree(jsonObject(reply));
      AiVerdict verdict =
          AiVerdict.valueOf(
              verdictJson.path("verdict").asText("UNCERTAIN").trim().toUpperCase(Locale.ROOT));
      AiCategory category =
          AiCategory.valueOf(
              verdictJson.path("category").asText("OTHER").trim().toUpperCase(Locale.ROOT));
      double confidence = verdictJson.path("confidence").asDouble(0.0);
      String reason = verdictJson.path("reason").asText("");
      return new ModerationResult(verdict, category, confidence, reason, model);
    } catch (Exception e) {
      throw new IllegalStateException("Failed to parse AI moderation response", e);
    }
  }

  /** The outermost {@code {...}} in the reply, so a markdown fence or stray words are ignored. */
  private static String jsonObject(String reply) {
    int start = reply.indexOf('{');
    int end = reply.lastIndexOf('}');
    if (start < 0 || end < start) {
      throw new IllegalArgumentException("no JSON object in reply");
    }
    return reply.substring(start, end + 1);
  }
}
