package org.smalltech.hashtaglocal_backend.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "issues")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class IssueEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	// Human-readable issue key (optional)
	@Column(unique = true, length = 50, name = "issue_key")
	private String key;

	@Column(nullable = false, length = 50000)
	private String description;

	@Column(nullable = false, length = 100)
	private String type;

	@Column(nullable = false, length = 100)
	private String status;

	@Column(name = "created_at", nullable = false, updatable = false)
	private String createdAt;

	@Column(name = "updated_at", nullable = false)
	private String updatedAt;
}
