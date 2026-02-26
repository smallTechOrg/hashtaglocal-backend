package org.smalltech.hashtaglocal_backend.model.response;

import java.time.LocalDateTime;
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
  private String eventName;
  private String organisation;

  /** The platform this event was sourced from (e.g., "mybharat.gov.in", "ivolunteer"). */
  private String platform;

  /** String name of the {@link org.smalltech.hashtaglocal_backend.model.EventTypeModel} enum. */
  private String eventType;

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

  /** URL to the event page on the source platform. */
  private String link;

  /** Free-form extra fields that vary per organisation or platform (stored as JSONB). */
  private Map<String, Object> metaData;

  /**
   * Geocoded location details extracted from the {@code locations} table. Exposes the PostGIS Point
   * as plain lat/lng doubles and includes the resolved Locality (city) name.
   */
  @Data
  @Builder
  public static class LocationData {
    private Long id;

    /** Latitude extracted from the PostGIS Point (point.getY()). */
    private Double lat;

    /** Longitude extracted from the PostGIS Point (point.getX()). */
    private Double lng;

    /** Human-readable name of the location (e.g., "Lalbagh Main Gate"). */
    private String name;

    /** Name of the resolved Locality (city/area polygon) this location falls within. */
    private String locality;
  }
}
