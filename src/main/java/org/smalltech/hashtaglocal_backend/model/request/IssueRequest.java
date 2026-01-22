package org.smalltech.hashtaglocal_backend.model.request;

import java.util.ArrayList;
import java.util.List;
import lombok.Data;

@Data
public class IssueRequest {
	private String type;
	private String description;
	private LocationRequest location;
	private List<MediaRequest> mediaUrls = new ArrayList<>();
}
