package org.smalltech.hashtaglocal_backend.model.request;

import java.util.Map;
import lombok.Data;

@Data
public class LocationRequest {
	private String lat;
	private String lng;

	private Map<String, Object> metaData;
}
