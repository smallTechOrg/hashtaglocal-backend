package org.smalltech.hashtaglocal_backend.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "issues")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class IssueEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	// Human-readable issue key (optional)
	@Column(unique = true, length = 50, name = "\"key\"")
	private String key;

	@Column(nullable = false, length = 1000)
	private String description;

	@Column(nullable = false, length = 100)
	private String type;

	@Column(nullable = false, length = 100)
	private StatusEntity status;

	@Column(nullable = false, updatable = false)
	private String createdAt;

	@Column(nullable = false)
	private String updatedAt;
}
