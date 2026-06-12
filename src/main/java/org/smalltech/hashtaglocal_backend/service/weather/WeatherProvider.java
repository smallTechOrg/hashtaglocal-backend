package org.smalltech.hashtaglocal_backend.service.weather;

/**
 * Pluggable source of daily weather data. The active implementation is chosen by the {@code
 * bulletin.weather.provider} property, so switching providers (Open-Meteo, MET Norway, a paid API,
 * ...) is a config change plus one new implementation — no caller changes.
 */
public interface WeatherProvider {

  /** Today's forecast snapshot for the given coordinates. */
  WeatherSnapshot fetchDaily(double lat, double lng);
}
