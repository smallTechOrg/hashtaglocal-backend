package org.smalltech.hashtaglocal_backend.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import lombok.*;
import org.smalltech.hashtaglocal_backend.model.IssueTypeModel;

@Entity
@Table(name = "news_articles", indexes = {
		@Index(name = "idx_news_locality_category", columnList = "locality_id, category"),
		@Index(name = "idx_news_published_at", columnList = "publishedAt"),
		@Index(name = "idx_news_external_id", columnList = "externalId", unique = true)})
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class NewsArticleEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	// External ID from NewsAPI to prevent duplicates
	@Column(nullable = false, unique = true, length = 500)
	private String externalId;

	@Column(nullable = false, length = 1000)
	private String title;

	@Column(columnDefinition = "TEXT")
	private String description;

	@Column(columnDefinition = "TEXT")
	private String content;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 100)
	private IssueTypeModel category;

	@Column(length = 100)
	private String sourceId;

	@Column(nullable = false, length = 255)
	private String sourceName;

	@Column(length = 255)
	private String author;

	@Column(nullable = false, length = 1000)
	private String url;

	@Column(length = 1000)
	private String urlToImage;

	@Column(nullable = false)
	private LocalDateTime publishedAt;

	// Link to locality (e.g., Bengaluru)
	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "locality_id")
	private Locality locality;

	@Column(nullable = false, updatable = false)
	private LocalDateTime createdAt;

	@Column(nullable = false)
	private LocalDateTime updatedAt;

	@PrePersist
	protected void onCreate() {
		createdAt = LocalDateTime.now();
		updatedAt = LocalDateTime.now();
	}

	@PreUpdate
	protected void onUpdate() {
		updatedAt = LocalDateTime.now();
	}
}
