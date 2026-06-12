package org.smalltech.hashtaglocal_backend.service.weather;

import java.util.HashMap;
import java.util.Map;
import lombok.Builder;
import lombok.Data;

/**
 * Provider-agnostic daily weather snapshot. Every field is nullable — providers fill what they
 * have; {@link #toDataMap()} produces the {@code periodic_data.data} JSONB payload.
 */
@Data
@Builder
public class WeatherSnapshot {

  private Double minTemp;
  private Double maxTemp;
  private Double humidity;
  private Double rainProbability;
  private Double avgAqi;
  private Double pollen;

  /** Provider identifier stored in {@code periodic_data.source}, e.g. OPEN_METEO. */
  private String source;

  public Map<String, Object> toDataMap() {
    Map<String, Object> data = new HashMap<>();
    data.put("min_temp", minTemp);
    data.put("max_temp", maxTemp);
    data.put("humidity", humidity);
    data.put("rain_probability", rainProbability);
    data.put("avg_aqi", avgAqi);
    data.put("pollen", pollen);
    return data;
  }
}
