package org.smalltech.hashtaglocal_backend.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "city_import_status", indexes = {@Index(name = "idx_city_name", columnList = "cityName"),
		@Index(name = "idx_status", columnList = "status")})
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CityImportStatus {
	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne
	@JoinColumn(name = "import_job_id", nullable = false)
	private ImportJob importJob;

	@Column(nullable = false)
	private String cityName;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	@Builder.Default
	private CityImportResult status = CityImportResult.PENDING;

	@Column(nullable = false)
	private LocalDateTime attemptedAt;

	private LocalDateTime completedAt;

	@Column(length = 2000)
	private String errorMessage;

	@Column(nullable = false)
	@Builder.Default
	private Integer attemptCount = 1;

	@ManyToOne
	@JoinColumn(name = "locality_id")
	private Locality locality;

	public enum CityImportResult {
		PENDING, SUCCESS, FAILED, SKIPPED, NO_DATA_FOUND, RATE_LIMITED
	}
}
