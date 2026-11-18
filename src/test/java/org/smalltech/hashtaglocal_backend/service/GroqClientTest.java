package org.smalltech.hashtaglocal_backend.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.ExpectedCount.times;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.smalltech.hashtaglocal_backend.service.weather.WeatherSnapshot;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.test.web.client.ResponseCreator;
import org.springframework.web.client.RestClient;

class GroqClientTest {

  private static final String URL = "http://groq.test/chat";
  private static final String AI_REPLY =
      "{\"choices\":[{\"message\":{\"content\":\"Skip the two-wheeler today.\"}}]}";
  private static final WeatherSnapshot WEATHER =
      WeatherSnapshot.builder().minTemp(21.0).maxTemp(29.0).rainProbability(47.0).build();

  private final RestClient.Builder builder = RestClient.builder();
  private final MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();

  // A huge rate keeps the spacing out of the way in tests that aren't about it.
  private GroqClient client(int requestsPerMinute) {
    GroqClient client =
        new GroqClient(builder, new ObjectMapper(), "key", URL, "model", requestsPerMinute);
    client.setBackoffBaseMillis(1); // real backoff is 2s, 4s, 8s...; don't wait that in tests
    return client;
  }

  private ResponseCreator overCapacity() {
    return withStatus(HttpStatus.SERVICE_UNAVAILABLE)
        .contentType(MediaType.APPLICATION_JSON)
        .body(
            "{\"error\":{\"message\":\"qwen/qwen3.8-27b is currently over capacity.\","
                + "\"type\":\"internal_server_error\"}}");
  }

  private GroqClient.WeatherSummary summarize(GroqClient client) {
    return client.generateWeatherSummary("Satara", WEATHER, List.of());
  }

  private ResponseCreator rateLimited(String retryAfter) {
    HttpHeaders headers = new HttpHeaders();
    headers.set(HttpHeaders.RETRY_AFTER, retryAfter);
    return withStatus(HttpStatus.TOO_MANY_REQUESTS).headers(headers);
  }

  @Test
  void retriesARateLimitedRequestAndReturnsTheAiSummary() {
    server.expect(times(2), requestTo(URL)).andRespond(rateLimited("0"));
    server
        .expect(once(), requestTo(URL))
        .andRespond(withSuccess(AI_REPLY, MediaType.APPLICATION_JSON));

    GroqClient.WeatherSummary summary = summarize(client(600_000));

    assertEquals("Skip the two-wheeler today.", summary.text());
    assertFalse(summary.fallback());
    server.verify();
  }

  @Test
  void fallsBackAfterRunningOutOfAttempts() {
    server.expect(times(5), requestTo(URL)).andRespond(rateLimited("0"));

    GroqClient.WeatherSummary summary = summarize(client(600_000));

    assertTrue(summary.fallback());
    assertEquals("Today: 21°C to 29°C, 47% chance of rain.", summary.text());
    server.verify();
  }

  @Test
  void doesNotWaitOutALongRateLimit() {
    server.expect(once(), requestTo(URL)).andRespond(rateLimited("3600"));

    assertTrue(summarize(client(600_000)).fallback());
    server.verify();
  }

  @Test
  void doesNotRetryOtherErrors() {
    server.expect(once(), requestTo(URL)).andRespond(withStatus(HttpStatus.UNAUTHORIZED));

    assertTrue(summarize(client(600_000)).fallback());
    server.verify();
  }

  @Test
  void retriesServiceUnavailableAndReturnsTheAiSummary() {
    server.expect(times(2), requestTo(URL)).andRespond(overCapacity());
    server
        .expect(once(), requestTo(URL))
        .andRespond(withSuccess(AI_REPLY, MediaType.APPLICATION_JSON));

    GroqClient.WeatherSummary summary = summarize(client(600_000));

    assertEquals("Skip the two-wheeler today.", summary.text());
    assertFalse(summary.fallback());
    assertNull(summary.failureReason());
    server.verify();
  }

  @Test
  void retriesOtherTransientServerErrors() {
    server.expect(once(), requestTo(URL)).andRespond(withStatus(HttpStatus.BAD_GATEWAY));
    server.expect(once(), requestTo(URL)).andRespond(withStatus(HttpStatus.GATEWAY_TIMEOUT));
    server
        .expect(once(), requestTo(URL))
        .andRespond(withSuccess(AI_REPLY, MediaType.APPLICATION_JSON));

    assertFalse(summarize(client(600_000)).fallback());
    server.verify();
  }

  @Test
  void retriesATimeoutOrIoError() {
    server
        .expect(once(), requestTo(URL))
        .andRespond(
            request -> {
              throw new IOException("Read timed out");
            });
    server
        .expect(once(), requestTo(URL))
        .andRespond(withSuccess(AI_REPLY, MediaType.APPLICATION_JSON));

    assertFalse(summarize(client(600_000)).fallback());
    server.verify();
  }

  @Test
  void fallsBackWithTheGroqErrorAfterRunningOutOfAttemptsOnServiceUnavailable() {
    server.expect(times(5), requestTo(URL)).andRespond(overCapacity());

    GroqClient.WeatherSummary summary = summarize(client(600_000));

    assertTrue(summary.fallback());
    assertEquals("Today: 21°C to 29°C, 47% chance of rain.", summary.text());
    assertTrue(summary.failureReason().contains("503"), summary.failureReason());
    assertTrue(summary.failureReason().contains("over capacity"), summary.failureReason());
    server.verify();
  }

  @Test
  void honoursRetryAfterOnServiceUnavailableAndGivesUpOnALongOne() {
    HttpHeaders headers = new HttpHeaders();
    headers.set(HttpHeaders.RETRY_AFTER, "3600");
    server
        .expect(once(), requestTo(URL))
        .andRespond(withStatus(HttpStatus.SERVICE_UNAVAILABLE).headers(headers));

    assertTrue(summarize(client(600_000)).fallback());
    server.verify();
  }

  @Test
  void doesNotRetryNonTransientServerErrors() {
    server.expect(once(), requestTo(URL)).andRespond(withStatus(HttpStatus.NOT_IMPLEMENTED));

    assertTrue(summarize(client(600_000)).fallback());
    server.verify();
  }

  @Test
  void backoffDoublesPerAttemptWithJitterAndIsCapped() {
    GroqClient client = client(600_000);
    client.setBackoffBaseMillis(2_000);

    assertInRange(client.backoffMillis(1), 2_000, 2_500);
    assertInRange(client.backoffMillis(2), 4_000, 5_000);
    assertInRange(client.backoffMillis(3), 8_000, 10_000);
    assertInRange(client.backoffMillis(4), 15_000, 18_750); // 16s capped to 15s, plus jitter
  }

  @Test
  void failureReasonIsSingleLineAndTruncated() {
    String reason =
        GroqClient.describeFailure(
            new IllegalStateException("line one\n  line two " + "x".repeat(400)));

    assertFalse(reason.contains("\n"));
    assertTrue(reason.startsWith("line one line two"));
    assertTrue(reason.length() <= 203, "length " + reason.length());
  }

  @Test
  void missingApiKeyFallsBackWithAReason() {
    GroqClient noKey = new GroqClient(builder, new ObjectMapper(), "", URL, "model", 600_000);

    GroqClient.WeatherSummary summary = summarize(noKey);

    assertTrue(summary.fallback());
    assertEquals("GROQ_API_KEY is not configured", summary.failureReason());
  }

  private static void assertInRange(long actual, long min, long max) {
    assertTrue(actual >= min && actual <= max, actual + " not in [" + min + ", " + max + "]");
  }

  @Test
  void spacesRequestsToTheConfiguredRate() {
    server
        .expect(times(3), requestTo(URL))
        .andRespond(withSuccess(AI_REPLY, MediaType.APPLICATION_JSON));
    GroqClient client = client(600); // one request every 100 ms

    long start = System.nanoTime();
    summarize(client);
    summarize(client);
    summarize(client);
    long elapsedMillis = (System.nanoTime() - start) / 1_000_000;

    assertTrue(elapsedMillis >= 190, "took only " + elapsedMillis + " ms");
    server.verify();
  }

  @Test
  void readsRetryAfterAndDefaultsWhenMissingOrUnparseable() {
    HttpHeaders headers = new HttpHeaders();
    assertEquals(5, GroqClient.retryWaitSeconds(null));
    assertEquals(5, GroqClient.retryWaitSeconds(headers));
    headers.set(HttpHeaders.RETRY_AFTER, "3");
    assertEquals(3, GroqClient.retryWaitSeconds(headers));
    headers.set(HttpHeaders.RETRY_AFTER, "Fri, 30 Oct 2026 02:30:00 GMT");
    assertEquals(5, GroqClient.retryWaitSeconds(headers));
  }
}
