package org.smalltech.hashtaglocal_backend.model.response;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import lombok.Builder;
import lombok.Data;

/**
 * API response model representing a single event.
 *
 * <p>Returned by {@code GET /api/v1/events}. Field names are serialised as snake_case in JSON
 *
 * <p>The nested {@link LocationData} is null when geocoding has not yet been done for this event —
 * consumers should fall back to the {@code address} field in that case.
 */
@Data
@Builder
public class EventData {

  private Long id;
  private String name;
  private String organisation;
  private String imageUrl;

  /** The portal this event was sourced from (e.g., "MYBHARATGOVIN", "IVOLUNTEER"). */
  private String portal;

  /** String name of the {@link org.smalltech.hashtaglocal_backend.model.EventTypeModel} enum. */
  private String type;

  private LocalDateTime startTime;

  /** Null if the event has no fixed end time. */
  private LocalDateTime endTime;

  /**
   * Geocoded location with lat/lng and locality. Null if geocoding has not been done yet — use
   * {@code address} as a fallback.
   */
  private LocationData location;

  /** Raw address string from the original data source. */
  private String address;

  /** URL to the event page on the source portal. */
  private String link;

  /** Free-form extra fields that vary per organisation or portal (stored as JSONB). */
  private Map<String, Object> metaData;

  @Data
  @Builder
  public static class LocationData {
    private Double lat;
    private Double lng;
    private String name;
    private LocalityData locality;
  }

  @Data
  @Builder
  public static class LocalityData {
    private List<String> hashtags;
  }
}
