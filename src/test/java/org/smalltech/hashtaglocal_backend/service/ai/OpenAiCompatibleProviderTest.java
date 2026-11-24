package org.smalltech.hashtaglocal_backend.service.ai;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.web.client.RestClient;

class OpenAiCompatibleProviderTest {

  private final OpenAiCompatibleProvider provider =
      new OpenAiCompatibleProvider(RestClient.builder(), new ObjectMapper());

  @Test
  void baseUrlGetsTheChatPathAndFullUrlIsKept() {
    assertEquals(
        "https://openrouter.ai/api/v1/chat/completions",
        OpenAiCompatibleProvider.chatUrl("https://openrouter.ai/api/v1"));
    assertEquals(
        "https://openrouter.ai/api/v1/chat/completions",
        OpenAiCompatibleProvider.chatUrl("https://openrouter.ai/api/v1/"));
    assertEquals(
        "https://api.together.xyz/v1/chat/completions",
        OpenAiCompatibleProvider.chatUrl(" https://api.together.xyz/v1/chat/completions "));
  }

  @Test
  void sendsMaxTokensNotMaxCompletionTokens() {
    Map<String, Object> body = OpenAiCompatibleProvider.body("deepseek/deepseek-r1", "hi", 700);

    assertEquals(700, body.get("max_tokens"));
    assertFalse(body.containsKey("max_completion_tokens"));
    assertFalse(body.containsKey("temperature"));
    assertEquals("deepseek/deepseek-r1", body.get("model"));
  }

  @Test
  void stripsThinkingBeforeTheAnswer() throws Exception {
    String raw =
        "{\"choices\":[{\"message\":{\"content\":\"<think>maybe {\\\"a\\\": 1}?</think>"
            + "{\\\"question\\\": \\\"Q\\\"}\"}}]}";

    assertEquals("{\"question\": \"Q\"}", provider.extractText(raw));
  }

  @Test
  void hasNoDefaultEndpointAndRefusesToCallWithoutOne() {
    assertNull(provider.defaultEndpoint());

    AiResult result = provider.call("", "model", "key-12345678", "prompt", 10);

    assertEquals(AiFailure.BAD_REQUEST, result.failure());
    assertTrue(result.detail().contains("no endpoint"));
  }

  @Test
  void classifiesCommonErrors() {
    HttpHeaders none = new HttpHeaders();
    assertEquals(AiFailure.AUTH_FAILED, provider.classify(401, none, "{}").failure());
    assertTrue(provider.classify(401, none, "{}").wholeKey());
    assertEquals(AiFailure.QUOTA_EXHAUSTED, provider.classify(402, none, "{}").failure());
    assertEquals(AiFailure.RATE_LIMITED, provider.classify(429, none, "{}").failure());
    assertEquals(AiFailure.MODEL_UNAVAILABLE, provider.classify(404, none, "{}").failure());
    assertEquals(
        AiFailure.MODEL_UNAVAILABLE,
        provider
            .classify(400, none, "{\"error\":{\"message\":\"The model `x` does not exist\"}}")
            .failure());
    assertEquals(AiFailure.BAD_REQUEST, provider.classify(400, none, "{}").failure());
    assertEquals(AiFailure.TRANSIENT, provider.classify(503, none, "{}").failure());
  }
}
