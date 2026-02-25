package org.smalltech.hashtaglocal_backend.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Tracks each locality import attempt with Google Maps validation results. One row per discovered
 * locality import in a job, showing status and errors.
 */
@Entity
@Table(name = "locality_import_status")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LocalityImportStatus {

  public enum ImportStatus {
    SUCCESS,
    FAILED,
    SKIPPED,
    NO_DATA_FOUND
  }

  public enum LocalityType {
    CITY,
    TOWN,
    DISTRICT
  }

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "import_job_id", nullable = false)
  private ImportJob importJob;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "discovered_locality_id")
  private DiscoveredLocality discoveredLocality;

  @Column(nullable = false)
  private String localityName;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  private LocalityType localityType;

  @Column(nullable = false)
  private String state;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  private ImportStatus importStatus;

  @Column(columnDefinition = "TEXT")
  private String errorMessage;

  @Column(columnDefinition = "jsonb")
  @org.hibernate.annotations.JdbcTypeCode(org.hibernate.type.SqlTypes.JSON)
  private String googleMapsMetadata;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "locality_id")
  private Locality locality;

  @Column(nullable = false)
  @Builder.Default
  private Integer attemptCount = 0;

  @Column(nullable = false, updatable = false)
  @Builder.Default
  private LocalDateTime createdAt = LocalDateTime.now();

  private LocalDateTime updatedAt;
}
