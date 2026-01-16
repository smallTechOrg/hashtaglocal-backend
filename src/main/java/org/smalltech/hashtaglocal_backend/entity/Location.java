package org.smalltech.hashtaglocal_backend.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "locations")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Location {
	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(nullable = false)
	private String lat;

	@Column(nullable = false)
	private String lng;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "locality_id", nullable = false)
	private Locality locality;

	@Column(nullable = false)
	private String name;

	@Column(columnDefinition = "TEXT")
	private String metaData;
}
