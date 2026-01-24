package org.smalltech.hashtaglocal_backend.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "import_jobs")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ImportJob {
	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(nullable = false)
	private LocalDateTime startedAt;

	private LocalDateTime completedAt;

	@Column(nullable = false)
	private Integer totalLocalities;

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
	@Column(nullable = false)
	@Builder.Default
	private ImportJobStatus status = ImportJobStatus.RUNNING;

	@Column(length = 1000)
	private String errorMessage;

	public enum ImportJobStatus {
		RUNNING, COMPLETED, FAILED, CANCELLED
	}
}
