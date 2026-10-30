package org.smalltech.hashtaglocal_backend.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.ExpectedCount.times;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.fasterxml.jackson.databind.ObjectMapper;
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
    return new GroqClient(builder, new ObjectMapper(), "key", URL, "model", requestsPerMinute);
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
