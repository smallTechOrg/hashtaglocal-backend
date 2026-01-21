package org.smalltech.hashtaglocal_backend.entity;

import jakarta.persistence.*;
import lombok.*;
import org.smalltech.hashtaglocal_backend.model.MediaTypeModel;

@Entity
@Table(name = "media")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MediaEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	// Media belongs to an Issue
	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "issue_id", nullable = false)
	private IssueEntity issue;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private MediaTypeModel type;

	@Column(nullable = false, length = 5000)
	private String url;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "location_id")
	private Location location;
}
