package org.smalltech.hashtaglocal_backend.entity;

import com.vladmihalcea.hibernate.type.json.JsonType;
import jakarta.persistence.*;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.Type;

/**
 * Generic, immutable daily snapshot of locality-specific data (weather for now). Intentionally
 * provider-agnostic: the payload lives in the {@code data} JSONB and {@code source} records which
 * provider produced it, so new data types or providers need no schema change.
 */
@Entity
@Table(
    name = "periodic_data",
    uniqueConstraints =
        @UniqueConstraint(
            name = "uq_periodic_data_locality_date_type",
            columnNames = {"locality_id", "date", "data_type"}))
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PeriodicDataEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "locality_id", nullable = false)
  private Locality locality;

  @Column(nullable = false)
  private LocalDate date;

  /** DAILY | WEEKLY. */
  @Column(nullable = false, length = 20)
  private String type;

  /** WEATHER (more data types later). */
  @Column(name = "data_type", nullable = false, length = 30)
  private String dataType;

  /**
   * Type-specific payload. For WEATHER: {@code min_temp}, {@code max_temp}, {@code humidity},
   * {@code avg_aqi}, {@code pollen}, {@code rain_probability}.
   */
  @Type(JsonType.class)
  @Column(columnDefinition = "jsonb")
  private Map<String, Object> data;

  /** Provider that produced the snapshot, e.g. OPEN_METEO | MANUAL | SCRAPER. */
  @Column(nullable = false, length = 30)
  private String source;

  @CreationTimestamp
  @Column(nullable = false, updatable = false)
  private LocalDateTime createdAt;
}
