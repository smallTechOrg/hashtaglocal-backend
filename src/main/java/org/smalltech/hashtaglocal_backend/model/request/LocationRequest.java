package org.smalltech.hashtaglocal_backend.model.request;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.Map;
import lombok.Data;

@Data
public class LocationRequest {
	private Double lat;
	private Double lng;
	@JsonProperty("meta_data")
	private Map<String, Object> metaData;
}
