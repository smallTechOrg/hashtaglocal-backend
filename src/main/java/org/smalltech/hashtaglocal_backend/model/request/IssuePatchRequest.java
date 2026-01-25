package org.smalltech.hashtaglocal_backend.model.request;

import lombok.Data;

@Data
public class IssuePatchRequest {
	private String status;
	private String type;
	private String description;
}
