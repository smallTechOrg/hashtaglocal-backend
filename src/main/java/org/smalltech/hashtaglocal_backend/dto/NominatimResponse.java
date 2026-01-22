package org.smalltech.hashtaglocal_backend.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import lombok.Data;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class NominatimResponse {
	@JsonProperty("place_id")
	private Long placeId;

	@JsonProperty("osm_id")
	private Long osmId;

	@JsonProperty("osm_type")
	private String osmType;

	@JsonProperty("display_name")
	private String displayName;

	private String name;
	private String type;

	@JsonProperty("geojson")
	private GeoJson geoJson;

	@Data
	@JsonIgnoreProperties(ignoreUnknown = true)
	public static class GeoJson {
		private String type;
		private List<List<List<Double>>> coordinates;

		// For MultiPolygon
		@JsonProperty("coordinates")
		private Object coordinatesRaw;
	}
}
