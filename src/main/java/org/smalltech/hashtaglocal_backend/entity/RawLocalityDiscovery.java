package org.smalltech.hashtaglocal_backend.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Stores raw locality discoveries from each source (GeoNames, OSM, India Post) before
 * deduplication. One row per source discovery.
 */
@Entity
@Table(name = "raw_locality_discoveries")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RawLocalityDiscovery {

  public enum DiscoverySource {
    GEONAMES,
    OSM,
    INDIAPOST
  }

  public enum LocalityType {
    CITY,
    TOWN,
    DISTRICT,
    UNKNOWN
  }

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "discovery_run_id", nullable = false)
  private LocalityDiscoveryRun discoveryRun;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  private DiscoverySource source;

  @Column(nullable = false)
  private String name;

  @Column(nullable = false)
  private String state;

  @Column(nullable = false, length = 10)
  private String countryCode;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  private LocalityType localityType;

  @Column(columnDefinition = "TEXT")
  private String sourceMetadata;

  @Column(nullable = false, updatable = false)
  @Builder.Default
  private LocalDateTime createdAt = LocalDateTime.now();
}
