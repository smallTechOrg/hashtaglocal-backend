package org.smalltech.hashtaglocal_backend.model.request;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import java.util.Map;
import lombok.Data;

@Data
public class LocationRequest {

	@NotNull(message = "lat is required")
	@DecimalMin(value = "-90.0", message = "lat must be between -90 and 90")
	@DecimalMax(value = "90.0", message = "lat must be between -90 and 90")
	private Double lat;

	@NotNull(message = "lng is required")
	@DecimalMin(value = "-180.0", message = "lng must be between -180 and 180")
	@DecimalMax(value = "180.0", message = "lng must be between -180 and 180")
	private Double lng;

	@JsonProperty("meta_data")
	private Map<String, Object> metaData;
}
