package org.smalltech.hashtaglocal_backend.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;
import java.util.Map;
import lombok.Data;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class OverpassResponse {
	private List<Element> elements;

	@Data
	@JsonIgnoreProperties(ignoreUnknown = true)
	public static class Element {
		private String type;
		private Long id;
		private Map<String, String> tags;
		private Double lat;
		private Double lon;

		// For ways and relations
		private List<Long> nodes;
		private List<Member> members;
		private Bounds bounds;
	}

	@Data
	@JsonIgnoreProperties(ignoreUnknown = true)
	public static class Member {
		private String type;
		private Long ref;
		private String role;
	}

	@Data
	@JsonIgnoreProperties(ignoreUnknown = true)
	public static class Bounds {
		private Double minlat;
		private Double minlon;
		private Double maxlat;
		private Double maxlon;
	}
}
