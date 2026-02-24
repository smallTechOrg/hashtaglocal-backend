package org.smalltech.hashtaglocal_backend.model.request;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import lombok.Data;

@Data
public class IssuePatchRequest {
	private String status;
	private String type;
	private String description;

	@DecimalMin(value = "-90.0", message = "lat must be greater than or equal to -90.0")
	@DecimalMax(value = "90.0", message = "lat must be less than or equal to 90.0")
	private Double lat;

	@DecimalMin(value = "-180.0", message = "lng must be greater than or equal to -180.0")
	@DecimalMax(value = "180.0", message = "lng must be less than or equal to 180.0")
	private Double lng;
}
