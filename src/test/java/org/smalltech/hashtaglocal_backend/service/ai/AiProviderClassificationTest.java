package org.smalltech.hashtaglocal_backend.service.ai;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.web.client.RestClient;

/** Each provider's HTTP error mapping and reply parsing, from realistic response bodies. */
class AiProviderClassificationTest {

  private final ObjectMapper mapper = new ObjectMapper();
  private final AnthropicProvider anthropic = new AnthropicProvider(RestClient.builder(), mapper);
  private final GroqProvider groq = new GroqProvider(RestClient.builder(), mapper);
  private final GeminiProvider gemini = new GeminiProvider(RestClient.builder(), mapper);

  // ── Anthropic ───────────────────────────────────────────────────────────

  @Test
  void anthropicNoCreditIsKeyWideQuota() {
    AiResult r =
        anthropic.classify(
            400,
            null,
            "{\"type\":\"error\",\"error\":{\"type\":\"invalid_request_error\","
                + "\"message\":\"Your credit balance is too low to access the Anthropic API.\"}}");
    assertEquals(AiFailure.QUOTA_EXHAUSTED, r.failure());
    assertTrue(r.wholeKey());
  }

  @Test
  void anthropicRateLimitUsesRetryAfter() {
    AiResult r = anthropic.classify(429, retryAfter("12"), "{\"error\":{\"type\":\"rate_limit\"}}");
    assertEquals(AiFailure.RATE_LIMITED, r.failure());
    assertEquals(Duration.ofSeconds(12), r.retryAfter());
    assertFalse(r.wholeKey());
  }

  @Test
  void anthropicOtherStatuses() {
    assertEquals(AiFailure.AUTH_FAILED, anthropic.classify(401, null, "").failure());
    assertTrue(anthropic.classify(401, null, "").wholeKey());
    assertEquals(AiFailure.MODEL_UNAVAILABLE, anthropic.classify(404, null, "").failure());
    assertEquals(AiFailure.TRANSIENT, anthropic.classify(529, null, "overloaded").failure());
    assertEquals(AiFailure.BAD_REQUEST, anthropic.classify(400, null, "bad field").failure());
  }

  @Test
  void anthropicJoinsTextBlocks() throws Exception {
    String raw =
        "{\"content\":[{\"type\":\"text\",\"text\":\"Hello \"},"
            + "{\"type\":\"tool_use\"},{\"type\":\"text\",\"text\":\"world\"}]}";
    assertEquals("Hello world", anthropic.extractText(raw));
  }

  // ── Groq ────────────────────────────────────────────────────────────────

  @Test
  void groqDailyLimitIsQuotaForThatModelOnly() {
    AiResult r =
        groq.classify(
            429,
            retryAfter("3600"),
            "{\"error\":{\"message\":\"Rate limit reached for model `m` on tokens per day"
                + " (TPD): Limit 500000\"}}");
    assertEquals(AiFailure.QUOTA_EXHAUSTED, r.failure());
    assertEquals(Duration.ofHours(1), r.retryAfter());
    assertFalse(r.wholeKey(), "Groq limits are per model, so other models on the key still work");
  }

  @Test
  void groqShortWaitIsRateLimit() {
    AiResult r = groq.classify(429, retryAfter("2.4"), "tokens per minute (TPM)");
    assertEquals(AiFailure.RATE_LIMITED, r.failure());
    assertEquals(Duration.ofSeconds(3), r.retryAfter());
  }

  @Test
  void groqOtherStatuses() {
    assertEquals(AiFailure.AUTH_FAILED, groq.classify(401, null, "").failure());
    assertEquals(
        AiFailure.MODEL_UNAVAILABLE,
        groq.classify(400, null, "{\"error\":{\"code\":\"model_decommissioned\"}}").failure());
    assertEquals(AiFailure.BAD_REQUEST, groq.classify(400, null, "bad field").failure());
    assertEquals(AiFailure.TRANSIENT, groq.classify(503, null, "").failure());
  }

  @Test
  void groqStripsThinking() throws Exception {
    String raw =
        "{\"choices\":[{\"message\":{\"content\":\"<think>{draft}</think>{\\\"q\\\":1}\"}}]}";
    assertEquals("{\"q\":1}", groq.extractText(raw));
  }

  // ── Gemini ──────────────────────────────────────────────────────────────

  @Test
  void geminiPerDayQuotaIsQuota() {
    AiResult r =
        gemini.classify(
            429,
            null,
            "{\"error\":{\"status\":\"RESOURCE_EXHAUSTED\",\"details\":[{\"@type\":"
                + "\"type.googleapis.com/google.rpc.QuotaFailure\",\"violations\":[{\"quotaId\":"
                + "\"GenerateRequestsPerDayPerProjectPerModel-FreeTier\"}]}]}}");
    assertEquals(AiFailure.QUOTA_EXHAUSTED, r.failure());
    assertFalse(r.wholeKey());
  }

  @Test
  void geminiPerMinuteLimitUsesRetryDelay() {
    AiResult r =
        gemini.classify(
            429,
            null,
            "{\"error\":{\"status\":\"RESOURCE_EXHAUSTED\",\"details\":[{\"@type\":"
                + "\"type.googleapis.com/google.rpc.QuotaFailure\",\"violations\":[{\"quotaId\":"
                + "\"GenerateRequestsPerMinutePerProjectPerModel-FreeTier\"}]},{\"@type\":"
                + "\"type.googleapis.com/google.rpc.RetryInfo\",\"retryDelay\":\"37.2s\"}]}}");
    assertEquals(AiFailure.RATE_LIMITED, r.failure());
    assertEquals(Duration.ofSeconds(38), r.retryAfter());
  }

  @Test
  void geminiInvalidKeyIsKeyWideAuth() {
    AiResult r =
        gemini.classify(
            400,
            null,
            "{\"error\":{\"message\":\"API key not valid. Please pass a valid API key.\","
                + "\"status\":\"INVALID_ARGUMENT\",\"details\":[{\"reason\":\"API_KEY_INVALID\"}]}}");
    assertEquals(AiFailure.AUTH_FAILED, r.failure());
    assertTrue(r.wholeKey());
  }

  @Test
  void geminiOtherStatuses() {
    assertEquals(AiFailure.MODEL_UNAVAILABLE, gemini.classify(404, null, "").failure());
    assertEquals(AiFailure.BAD_REQUEST, gemini.classify(400, null, "bad field").failure());
    assertEquals(AiFailure.TRANSIENT, gemini.classify(503, null, "UNAVAILABLE").failure());
  }

  @Test
  void geminiSkipsThoughtParts() throws Exception {
    String raw =
        "{\"candidates\":[{\"content\":{\"parts\":["
            + "{\"text\":\"thinking...\",\"thought\":true},{\"text\":\"answer\"}]}}]}";
    assertEquals("answer", gemini.extractText(raw));
  }

  // ── Shared ──────────────────────────────────────────────────────────────

  @Test
  void unreadableRetryAfterIsNull() {
    assertNull(RestAiProvider.retryAfter(retryAfter("Wed, 21 Oct 2026 07:28:00 GMT")));
    assertNull(RestAiProvider.retryAfter(null));
  }

  private static HttpHeaders retryAfter(String value) {
    HttpHeaders headers = new HttpHeaders();
    headers.set(HttpHeaders.RETRY_AFTER, value);
    return headers;
  }
}
