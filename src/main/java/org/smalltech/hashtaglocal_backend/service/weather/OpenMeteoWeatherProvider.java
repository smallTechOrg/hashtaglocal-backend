package org.smalltech.hashtaglocal_backend.service.weather;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
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
    } catch (Exception e) {
      throw new IllegalStateException(
          "Open-Meteo fetch failed for (" + lat + "," + lng + "): " + e.getMessage(), e);
    }
  }

  private Double firstValue(JsonNode daily, String field) {
    JsonNode value = daily.path(field).path(0);
    return value.isNumber() ? value.asDouble() : null;
  }
}
