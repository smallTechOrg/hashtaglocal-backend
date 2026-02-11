package org.smalltech.hashtaglocal_backend.model.request;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;

@JsonInclude(JsonInclude.Include.NON_NULL)
@Data
public class MediaRequest {
	private String type; // PHOTO / VIDEO
	private String url;
	private LocationRequest location;
	private String description;
}
