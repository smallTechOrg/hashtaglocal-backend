package org.smalltech.hashtaglocal_backend.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO for Google Maps Geocoding API response.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonIgnoreProperties(ignoreUnknown = true)
public class GoogleMapsLocationDTO {

	private List<Result> results;

	private String status;

	@Data
	@NoArgsConstructor
	@AllArgsConstructor
	@Builder
	@JsonIgnoreProperties(ignoreUnknown = true)
	public static class Result {

		private String formattedAddress;

		private Geometry geometry;

		private String placeId;

		private List<String> types;
	}

	@Data
	@NoArgsConstructor
	@AllArgsConstructor
	@Builder
	@JsonIgnoreProperties(ignoreUnknown = true)
	public static class Geometry {

		private Location location;

		private Viewport viewport;
	}

	@Data
	@NoArgsConstructor
	@AllArgsConstructor
	@Builder
	@JsonIgnoreProperties(ignoreUnknown = true)
	public static class Viewport {

		private Location northeast;

		private Location southwest;
	}

	@Data
	@NoArgsConstructor
	@AllArgsConstructor
	@Builder
	@JsonIgnoreProperties(ignoreUnknown = true)
	public static class Location {

		private Double lat;

		private Double lng;
	}
}
