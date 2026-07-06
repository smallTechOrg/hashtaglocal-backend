package org.smalltech.hashtaglocal_backend.service.weather;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriComponentsBuilder;

/**
 * Weather via the free Open-Meteo forecast API (no key required). Active when {@code
 * bulletin.weather.provider=open-meteo} (the default).
 */
@Component
@Slf4j
@ConditionalOnProperty(
    name = "bulletin.weather.provider",
    havingValue = "open-meteo",
    matchIfMissing = true)
public class OpenMeteoWeatherProvider implements WeatherProvider {

  public static final String SOURCE = "OPEN_METEO";

  // Open-Meteo's free tier occasionally answers "the service is overloaded" (503), especially
  // around the top of the hour when the daily bulletin cron fires — retry a couple of times
  // with a short backoff before giving up on the locality for the day.
  private static final int MAX_ATTEMPTS = 3;
  private static final long RETRY_BACKOFF_MILLIS = 1000;

  private final RestClient restClient;
  private final ObjectMapper objectMapper;
  private final String baseUrl;

  public OpenMeteoWeatherProvider(
      RestClient.Builder restClientBuilder,
      ObjectMapper objectMapper,
      @Value("${bulletin.weather.open-meteo.url:https://api.open-meteo.com/v1/forecast}")
          String baseUrl) {
    this.restClient = restClientBuilder.build();
    this.objectMapper = objectMapper;
    this.baseUrl = baseUrl;
  }

  @Override
  public WeatherSnapshot fetchDaily(double lat, double lng) {
    String url =
        UriComponentsBuilder.fromUriString(baseUrl)
            .queryParam("latitude", lat)
            .queryParam("longitude", lng)
            .queryParam(
                "daily",
                "temperature_2m_max,temperature_2m_min,precipitation_probability_max,"
                    + "relative_humidity_2m_mean")
            .queryParam("forecast_days", 1)
            .queryParam("timezone", "auto")
            .build()
            .toUriString();

    Exception lastFailure = null;
    for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
      try {
        String raw = restClient.get().uri(url).retrieve().body(String.class);
        JsonNode daily = objectMapper.readTree(raw).path("daily");
        return WeatherSnapshot.builder()
            .minTemp(firstValue(daily, "temperature_2m_min"))
            .maxTemp(firstValue(daily, "temperature_2m_max"))
            .humidity(firstValue(daily, "relative_humidity_2m_mean"))
            .rainProbability(firstValue(daily, "precipitation_probability_max"))
            .source(SOURCE)
            .build();
      } catch (HttpServerErrorException | ResourceAccessException e) {
        lastFailure = e;
        log.warn(
            "Open-Meteo fetch attempt {}/{} failed for ({},{}): {}",
            attempt,
            MAX_ATTEMPTS,
            lat,
            lng,
            e.getMessage());
        if (attempt < MAX_ATTEMPTS) {
          sleep(RETRY_BACKOFF_MILLIS * attempt);
        }
      } catch (Exception e) {
        throw new IllegalStateException(
            "Open-Meteo fetch failed for (" + lat + "," + lng + "): " + e.getMessage(), e);
      }
    }
    throw new IllegalStateException(
        "Open-Meteo fetch failed for ("
            + lat
            + ","
            + lng
            + ") after "
            + MAX_ATTEMPTS
            + " attempts: "
            + lastFailure.getMessage(),
        lastFailure);
  }

  private void sleep(long millis) {
    try {
      Thread.sleep(millis);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
    }
  }

  private Double firstValue(JsonNode daily, String field) {
    JsonNode value = daily.path(field).path(0);
    return value.isNumber() ? value.asDouble() : null;
  }
}
