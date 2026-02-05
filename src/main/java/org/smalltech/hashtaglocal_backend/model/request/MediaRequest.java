package org.smalltech.hashtaglocal_backend.model.request;

import lombok.Data;

@Data
public class MediaRequest {
	private String type; // PHOTO / VIDEO
	private String url;
	private LocationRequest location;
	private String description;
}
