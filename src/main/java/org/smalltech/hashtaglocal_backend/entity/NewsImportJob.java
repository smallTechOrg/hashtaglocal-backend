package org.smalltech.hashtaglocal_backend.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import lombok.*;

@Entity
@Table(name = "news_import_jobs")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class NewsImportJob {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "locality_id")
	private Locality locality;

	@Column(nullable = false)
	private LocalDateTime importedAt;

	@Column(nullable = false)
	private Integer articlesImported;

	@Column(nullable = false)
	private Integer articlesDuplicate;

	@Column(nullable = false)
	@Enumerated(EnumType.STRING)
	private ImportStatus status;

	@Column(length = 1000)
	private String errorMessage;

	@Column(nullable = false, updatable = false)
	private LocalDateTime createdAt;

	public enum ImportStatus {
		SUCCESS, FAILED, PARTIAL
	}

	@PrePersist
	protected void onCreate() {
		createdAt = LocalDateTime.now();
	}
}
