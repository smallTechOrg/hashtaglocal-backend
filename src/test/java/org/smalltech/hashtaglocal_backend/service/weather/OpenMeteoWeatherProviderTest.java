package org.smalltech.hashtaglocal_backend.service.weather;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

/**
 * Covers the retry-with-backoff added to {@link OpenMeteoWeatherProvider} after the 8 AM cron
 * repeatedly lost an entire run to Open-Meteo's transient "service is overloaded" 503 with no retry
 * in place
 */
class OpenMeteoWeatherProviderTest {

  private static final String DAILY_JSON =
      "{\"daily\":{\"temperature_2m_max\":[30.0],\"temperature_2m_min\":[20.0],"
          + "\"precipitation_probability_max\":[10.0],\"relative_humidity_2m_mean\":[60.0]}}";

  @Test
  void succeedsAfterTransient503sWithinMaxAttempts() {
    RestClient.Builder builder = RestClient.builder();
    MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();

    server
        .expect(method(HttpMethod.GET))
        .andRespond(withServerError().body("{\"error\":true,\"reason\":\"overloaded\"}"));
    server
        .expect(method(HttpMethod.GET))
        .andRespond(withServerError().body("{\"error\":true,\"reason\":\"overloaded\"}"));
    server
        .expect(method(HttpMethod.GET))
        .andRespond(withSuccess(DAILY_JSON, MediaType.APPLICATION_JSON));

    OpenMeteoWeatherProvider provider =
        new OpenMeteoWeatherProvider(
            builder, new ObjectMapper(), "https://api.open-meteo.com/v1/forecast");

    WeatherSnapshot snapshot = provider.fetchDaily(12.9, 77.6);

    assertEquals(30.0, snapshot.getMaxTemp());
    assertEquals(20.0, snapshot.getMinTemp());
    server.verify();
  }

  @Test
  void throwsAfterExhaustingRetriesOnPersistent503() {
    RestClient.Builder builder = RestClient.builder();
    MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();

    server
        .expect(method(HttpMethod.GET))
        .andRespond(withServerError().body("{\"error\":true,\"reason\":\"overloaded\"}"));
    server
        .expect(method(HttpMethod.GET))
        .andRespond(withServerError().body("{\"error\":true,\"reason\":\"overloaded\"}"));
    server
        .expect(method(HttpMethod.GET))
        .andRespond(withServerError().body("{\"error\":true,\"reason\":\"overloaded\"}"));

    OpenMeteoWeatherProvider provider =
        new OpenMeteoWeatherProvider(
            builder, new ObjectMapper(), "https://api.open-meteo.com/v1/forecast");

    assertThrows(IllegalStateException.class, () -> provider.fetchDaily(12.9, 77.6));
    server.verify();
  }
}
