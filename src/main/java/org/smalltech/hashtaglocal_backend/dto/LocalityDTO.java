package org.smalltech.hashtaglocal_backend.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO for returning locality information with polygon data. Optimized for Google Maps rendering.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LocalityDTO {
  private Long id;
  private String hashtag;
  private String name;

  @JsonProperty("geoBoundary")
  private PolygonDTO geoBoundary;

  @Data
  @NoArgsConstructor
  @AllArgsConstructor
  @Builder
  public static class PolygonDTO {
    private String type; // "Polygon"
    private double[][][] coordinates; // GeoJSON format: [[[lng, lat], [lng, lat], ...]]
  }
}
