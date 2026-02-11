package org.smalltech.hashtaglocal_backend.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * Represents deduplicated localities after merging raw discoveries from
 * multiple sources. One row per unique locality (by name + state), containing
 * merged information and confidence scores from all matching sources.
 */
@Entity
@Table(name = "discovered_localities")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DiscoveredLocality {

	public enum LocalityType {
		CITY, TOWN, DISTRICT
	}

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "discovery_run_id", nullable = false)
	private LocalityDiscoveryRun discoveryRun;

	@Column(nullable = false)
	private String officialName;

	@JdbcTypeCode(SqlTypes.JSON)
	@Column(columnDefinition = "jsonb", nullable = false)
	private List<String> alternateNames;

	@Column(nullable = false)
	private String state;

	@Column(nullable = false, length = 10)
	private String countryCode;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private LocalityType localityType;

	@JdbcTypeCode(SqlTypes.JSON)
	@Column(columnDefinition = "jsonb", nullable = false)
	private List<String> sources;

	@Column(nullable = false)
	private Integer confidenceScore;

	@Column(nullable = false, updatable = false)
	@Builder.Default
	private LocalDateTime createdAt = LocalDateTime.now();
}
