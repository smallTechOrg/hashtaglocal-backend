package org.smalltech.hashtaglocal_backend.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.smalltech.hashtaglocal_backend.service.ai.AiFailure;
import org.smalltech.hashtaglocal_backend.service.ai.AiFallbackChain;
import org.smalltech.hashtaglocal_backend.service.ai.AiFallbackChain.AiCompletion;
import org.smalltech.hashtaglocal_backend.service.ai.AiProviderException;
import org.smalltech.hashtaglocal_backend.service.weather.WeatherSnapshot;

class BulletinAiClientTest {

  private static final WeatherSnapshot WEATHER =
      WeatherSnapshot.builder().minTemp(21.0).maxTemp(29.0).rainProbability(47.0).build();
  private static final Optional<AiCompletion> REPLY =
      Optional.of(new AiCompletion("Skip the two-wheeler today.", "claude/model"));

  private final AiFallbackChain ai = mock(AiFallbackChain.class);

  // A huge rate and no retry wait keep the timing out of the way in tests that aren't about it.
  private BulletinAiClient client(int requestsPerMinute) {
    return new BulletinAiClient(ai, requestsPerMinute, Duration.ZERO);
  }

  private BulletinAiClient.WeatherSummary summarize(BulletinAiClient client) {
    return client.generateWeatherSummary("Satara", WEATHER, List.of());
  }

  private static AiProviderException failure(AiFailure failure) {
    return new AiProviderException("failed", failure);
  }

  @Test
  void returnsTheAiSummaryFromTheAiProvider() {
    when(ai.complete(contains("Satara"), anyInt())).thenReturn(REPLY);

    BulletinAiClient.WeatherSummary summary = summarize(client(600_000));

    assertEquals("Skip the two-wheeler today.", summary.text());
    assertFalse(summary.fallback());
  }

  @Test
  void retriesARateLimitedRequestAndReturnsTheAiSummary() {
    when(ai.complete(anyString(), anyInt()))
        .thenThrow(failure(AiFailure.RATE_LIMITED))
        .thenReturn(REPLY);

    BulletinAiClient.WeatherSummary summary = summarize(client(600_000));

    assertEquals("Skip the two-wheeler today.", summary.text());
    assertFalse(summary.fallback());
    verify(ai, times(2)).complete(anyString(), anyInt());
  }

  @Test
  void fallsBackAfterRunningOutOfAttempts() {
    when(ai.complete(anyString(), anyInt())).thenThrow(failure(AiFailure.RATE_LIMITED));

    BulletinAiClient.WeatherSummary summary = summarize(client(600_000));

    assertTrue(summary.fallback());
    assertEquals("Today: 21°C to 29°C, 47% chance of rain.", summary.text());
    verify(ai, times(3)).complete(anyString(), anyInt());
  }

  @Test
  void doesNotRetryOtherErrors() {
    when(ai.complete(anyString(), anyInt())).thenThrow(failure(AiFailure.AUTH_FAILED));

    assertTrue(summarize(client(600_000)).fallback());
    verify(ai, times(1)).complete(anyString(), anyInt());
  }

  @Test
  void fallsBackWhenTheAiHasNoReply() {
    when(ai.complete(anyString(), anyInt())).thenReturn(Optional.empty());

    assertTrue(summarize(client(600_000)).fallback());
  }

  @Test
  void quizExplanationIsEmptyWhenTheAiFails() {
    when(ai.complete(anyString(), anyInt())).thenThrow(failure(AiFailure.TRANSIENT));

    assertEquals("", client(600_000).generateQuizExplanation("Q?", "A"));
  }

  @Test
  void spacesRequestsToTheConfiguredRate() {
    when(ai.complete(anyString(), anyInt())).thenReturn(REPLY);
    BulletinAiClient client = client(600); // one request every 100 ms

    long start = System.nanoTime();
    summarize(client);
    summarize(client);
    summarize(client);
    long elapsedMillis = (System.nanoTime() - start) / 1_000_000;

    assertTrue(elapsedMillis >= 190, "took only " + elapsedMillis + " ms");
  }
}
