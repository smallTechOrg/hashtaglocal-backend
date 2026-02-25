package org.smalltech.hashtaglocal_backend.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Tracks each locality discovery run, recording all raw discoveries across multiple sources before
 * deduplication.
 */
@Entity
@Table(name = "locality_discovery_runs")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LocalityDiscoveryRun {

  public enum DiscoveryStatus {
    IN_PROGRESS,
    COMPLETED,
    FAILED,
    CANCELLED
  }

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(nullable = false, length = 10)
  private String countryCode;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  @Builder.Default
  private DiscoveryStatus status = DiscoveryStatus.IN_PROGRESS;

  @Column(nullable = false)
  private LocalDateTime startedAt;

  private LocalDateTime completedAt;

  @Column(nullable = false)
  @Builder.Default
  private Integer totalRawDiscoveries = 0;

  @Column(nullable = false)
  @Builder.Default
  private Integer geonamesCount = 0;

  @Column(nullable = false)
  @Builder.Default
  private Integer osmCount = 0;

  @Column(nullable = false)
  @Builder.Default
  private Integer indiaPostCount = 0;

  @Column(length = 1000)
  private String errorMessage;
}
