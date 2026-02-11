package org.smalltech.hashtaglocal_backend.dto;

import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO for discovered locality with alternate names and confidence score.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DiscoveredLocalityDTO {

	private String officialName;

	private List<String> alternateNames;

	private String state;

	private String countryCode;

	private String localityType;

	private List<String> sources;

	private Integer confidenceScore;
}
