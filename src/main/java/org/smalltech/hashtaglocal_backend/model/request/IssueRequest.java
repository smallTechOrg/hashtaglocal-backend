package org.smalltech.hashtaglocal_backend.model.request;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.ArrayList;
import java.util.List;
import lombok.Data;

@Data
public class IssueRequest {

	@NotBlank(message = "type is required")
	private String type;

	private String description;

	@Valid
	@NotNull(message = "location is required")
	private LocationRequest location;

	@Valid
	@JsonProperty("media_urls")
	@NotNull(message = "media_urls is required")
	private List<MediaRequest> mediaUrls = new ArrayList<>();
}
