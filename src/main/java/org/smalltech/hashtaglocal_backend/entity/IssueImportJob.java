package org.smalltech.hashtaglocal_backend.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.smalltech.hashtaglocal_backend.model.IssueImportSource;

@Entity
@Table(name = "issue_import_jobs")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class IssueImportJob {

  public enum JobStatus {
    RUNNING,
    COMPLETED,
    FAILED,
    CANCELLED
  }

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 50)
  private IssueImportSource source;

  @Column(nullable = false)
  private LocalDateTime startedAt;

  private LocalDateTime completedAt;

  @Column(nullable = false)
  @Builder.Default
  private Integer totalIssues = 0;

  @Column(nullable = false)
  @Builder.Default
  private Integer successCount = 0;

  @Column(nullable = false)
  @Builder.Default
  private Integer failureCount = 0;

  @Column(nullable = false)
  @Builder.Default
  private Integer skippedCount = 0;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 50)
  @Builder.Default
  private JobStatus status = JobStatus.RUNNING;

  @Column(length = 2000)
  private String errorMessage;
}
