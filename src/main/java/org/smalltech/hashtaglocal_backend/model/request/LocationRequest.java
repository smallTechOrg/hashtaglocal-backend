package org.smalltech.hashtaglocal_backend.model.request;

import java.util.Map;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

@Data
public class LocationRequest {
	private String lat;
	private String lng;
	@JsonProperty("meta_data")
	private Map<String, Object> metaData;
}
