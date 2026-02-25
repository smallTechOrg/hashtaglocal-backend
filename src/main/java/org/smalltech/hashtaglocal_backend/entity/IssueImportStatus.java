package org.smalltech.hashtaglocal_backend.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.smalltech.hashtaglocal_backend.model.IssueImportSource;

@Entity
@Table(
    name = "issue_import_status",
    uniqueConstraints = {@UniqueConstraint(columnNames = {"source", "source_issue_id"})})
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class IssueImportStatus {

  public enum ImportStatus {
    SUCCESS,
    FAILED,
    SKIPPED
  }

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "job_id", nullable = false)
  private IssueImportJob job;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "issue_id")
  private IssueEntity issue;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 50)
  private IssueImportSource source;

  @Column(name = "source_issue_id", nullable = false, length = 200)
  private String sourceIssueId;

  @Column(name = "source_created_at")
  private LocalDateTime sourceCreatedAt;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 50)
  @Builder.Default
  private ImportStatus importStatus = ImportStatus.SKIPPED;

  @Column(length = 2000)
  private String errorMessage;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(columnDefinition = "jsonb")
  private String sourcePayload;

  @Column(length = 500)
  private String storedMediaPath;

  @Column(nullable = false)
  @Builder.Default
  private Integer attemptCount = 1;

  @Column(nullable = false, updatable = false)
  @Builder.Default
  private LocalDateTime createdAt = LocalDateTime.now();

  private LocalDateTime updatedAt;
}
