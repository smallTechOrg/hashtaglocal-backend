package org.smalltech.hashtaglocal_backend.service.ai;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;

class OpenAiCompatibleClientTest {

  private static final String REPLY =
      "{\"choices\":[{\"message\":{\"content\":\"{\\\"question\\\": \\\"Q\\\"}\"}}]}";

  /** Records each request instead of sending it. */
  private static class FakeServer extends OpenAiCompatibleClient {
    final List<String> urls = new ArrayList<>();
    final List<Map<String, Object>> bodies = new ArrayList<>();
    boolean rejectMaxTokens;

    FakeServer() {
      super(RestClient.builder(), new ObjectMapper());
    }

    @Override
    protected String send(String url, String apiKey, Map<String, Object> body) {
      urls.add(url);
      bodies.add(body);
      if (rejectMaxTokens && body.containsKey("max_tokens")) {
        throw HttpClientErrorException.create(
            HttpStatus.BAD_REQUEST,
            "Bad Request",
            new HttpHeaders(),
            ("{\"error\":{\"message\":\"Unsupported parameter: 'max_tokens'. Use"
                    + " 'max_completion_tokens' instead.\"}}")
                .getBytes(StandardCharsets.UTF_8),
            StandardCharsets.UTF_8);
      }
      return REPLY;
    }
  }

  private final OpenAiCompatibleClient client =
      new OpenAiCompatibleClient(RestClient.builder(), new ObjectMapper());

  @Test
  void baseUrlGetsTheChatPathAndFullUrlIsKept() {
    assertEquals(
        "https://api.anthropic.com/v1/chat/completions",
        OpenAiCompatibleClient.chatUrl("https://api.anthropic.com/v1"));
    assertEquals(
        "https://generativelanguage.googleapis.com/v1beta/openai/chat/completions",
        OpenAiCompatibleClient.chatUrl("https://generativelanguage.googleapis.com/v1beta/openai/"));
    assertEquals(
        "https://api.together.xyz/v1/chat/completions",
        OpenAiCompatibleClient.chatUrl(" https://api.together.xyz/v1/chat/completions "));
  }

  @Test
  void sendsMaxTokensByDefault() {
    FakeServer server = new FakeServer();

    AiResult result = server.call("https://api.example.com/v1", "m", "key-12345678", "hi", 700);

    assertTrue(result.isSuccess());
    assertEquals("{\"question\": \"Q\"}", result.text());
    assertEquals(List.of("https://api.example.com/v1/chat/completions"), server.urls);
    assertEquals(700, server.bodies.get(0).get("max_tokens"));
    assertFalse(server.bodies.get(0).containsKey("temperature"));
  }

  @Test
  void retriesWithMaxCompletionTokensWhenTheServerAsksForIt() {
    FakeServer server = new FakeServer();
    server.rejectMaxTokens = true; // as OpenAI's reasoning models do

    AiResult result = server.call("https://api.openai.com/v1", "gpt-x", "key-12345678", "hi", 700);

    assertTrue(result.isSuccess());
    assertEquals(2, server.bodies.size());
    assertEquals(700, server.bodies.get(1).get("max_completion_tokens"));
    assertFalse(server.bodies.get(1).containsKey("max_tokens"));
  }

  @Test
  void stripsThinkingBeforeTheAnswer() throws Exception {
    String raw =
        "{\"choices\":[{\"message\":{\"content\":\"<think>maybe {\\\"a\\\": 1}?</think>"
            + "{\\\"question\\\": \\\"Q\\\"}\"}}]}";

    assertEquals("{\"question\": \"Q\"}", client.extractText(raw));
  }

  @Test
  void refusesToCallWithoutAnEndpoint() {
    AiResult result = client.call("", "model", "key-12345678", "prompt", 10);

    assertEquals(AiFailure.BAD_REQUEST, result.failure());
  }

  @Test
  void classifiesCommonErrors() {
    HttpHeaders none = new HttpHeaders();
    assertEquals(AiFailure.AUTH_FAILED, client.classify(401, none, "{}").failure());
    assertTrue(client.classify(401, none, "{}").wholeKey());
    assertEquals(AiFailure.QUOTA_EXHAUSTED, client.classify(402, none, "{}").failure());
    assertEquals(
        AiFailure.QUOTA_EXHAUSTED,
        client
            .classify(400, none, "{\"error\":{\"message\":\"Your credit balance is too low\"}}")
            .failure());
    assertEquals(AiFailure.RATE_LIMITED, client.classify(429, none, "{}").failure());
    assertEquals(AiFailure.MODEL_UNAVAILABLE, client.classify(404, none, "{}").failure());
    assertEquals(
        AiFailure.MODEL_UNAVAILABLE,
        client
            .classify(400, none, "{\"error\":{\"message\":\"The model `x` does not exist\"}}")
            .failure());
    assertEquals(AiFailure.BAD_REQUEST, client.classify(400, none, "{}").failure());
    assertEquals(AiFailure.TRANSIENT, client.classify(503, none, "{}").failure());
  }

  @Test
  void masksKeysEchoedInErrors() {
    AiResult result =
        client.classify(
            401,
            new HttpHeaders(),
            "{\"error\":{\"message\":\"Incorrect API key provided: sk-proj-abc*****wxyz\"}}");

    assertFalse(result.detail().contains("sk-proj"), result.detail());
  }
}
