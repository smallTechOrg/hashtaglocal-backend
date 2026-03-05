package org.smalltech.hashtaglocal_backend.entity;

import com.vladmihalcea.hibernate.type.json.JsonType;
import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.Map;
import lombok.*;
import org.hibernate.annotations.Type;
import org.smalltech.hashtaglocal_backend.model.EventPortalModel;
import org.smalltech.hashtaglocal_backend.model.EventTypeModel;

/**
 * JPA entity mapped to the `events` table in PostgreSQL.
 *
 * <p>Represents a civic or community event (e.g., a beach cleanup, trek, or workshop) sourced from
 * platforms like digitalindia.gov.in, mybharat.gov.in, ivolunteer, or Team Everest.
 *
 * <p>Location strategy: - `address` stores the raw address string immediately on import. -
 * `location` (FK to the `locations` table) stores the geocoded PostGIS Point (lat/lng) and is
 * populated later once geocoding is done. The frontend can fall back to `address` if `location` is
 * null.
 */
@Entity
@Table(name = "events")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class EventEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "name", nullable = false)
  private String eventName;

  @Column private String organisation;

  /** URL to the event banner/thumbnail image provided by the source portal. */
  @Column(length = 2048)
  private String imageUrl;

  /**
   * Source portal stored as VARCHAR in DB, enforced as enum in Java.
   *
   * @see EventPortalModel
   */
  @Enumerated(EnumType.STRING)
  @Column(name = "portal", length = 50, nullable = false)
  private EventPortalModel portal;

  /**
   * Event activity type stored as VARCHAR in DB, enforced as enum in Java.
   *
   * @see EventTypeModel
   */
  @Enumerated(EnumType.STRING)
  @Column(name = "type", length = 50, nullable = false)
  private EventTypeModel eventType;

  /** Event start date and time. Required. */
  @Column(nullable = false)
  private LocalDateTime startTime;

  /** Event end date and time. Null if the event has no fixed end time. */
  @Column private LocalDateTime endTime;

  /**
   * FK to the `locations` table which stores a PostGIS Point (lat/lng) and links to a Locality
   * (city polygon). Null until geocoding is completed for this event's address.
   */
  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "location_id")
  private Location location;

  /**
   * Raw address string copied directly from the event data source (e.g., the Excel sheet). Kept as
   * a human-readable fallback while `location_id` is still null.
   */
  @Column(length = 1024, nullable = false)
  private String address;

  /** URL to the original event page on the source platform. */
  @Column(length = 2048, nullable = false)
  private String link;

  /**
   * Flexible JSONB column for extra fields that vary per organisation or platform. Examples: {
   * "city": "Bangalore", "entry_fee": "free", "registration_required": true }
   */
  @Type(JsonType.class)
  @Column(columnDefinition = "jsonb")
  private Map<String, Object> metaData;

  /** Timestamp when this record was first created. Set automatically, never updated. */
  @Column(nullable = false, updatable = false)
  private LocalDateTime createdAt;

  /** Timestamp of the most recent update to this record. Set automatically on every save. */
  @Column(nullable = false)
  private LocalDateTime updatedAt;

  /** Runs before INSERT — sets both timestamps to now. */
  @PrePersist
  protected void onCreate() {
    createdAt = LocalDateTime.now();
    updatedAt = LocalDateTime.now();
  }

  /** Runs before UPDATE — bumps updatedAt to now. */
  @PreUpdate
  protected void onUpdate() {
    updatedAt = LocalDateTime.now();
  }
}
