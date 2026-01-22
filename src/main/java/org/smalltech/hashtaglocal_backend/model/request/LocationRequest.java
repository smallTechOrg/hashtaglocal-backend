package org.smalltech.hashtaglocal_backend.model.request;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.Map;
import lombok.Data;

@Data
public class LocationRequest {
	private String lat;
	private String lng;
	@JsonProperty("meta_data")
	private Map<String, Object> metaData;
}
