package org.smalltech.hashtaglocal_backend.model.request;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import lombok.Data;

@Data
public class IssueVerifyRequest {

	@Valid
	@JsonProperty("issue_action")
	@NotNull(message = "issue_action is required")
	private IssueActionRequest issueAction;

	@Data
	public static class IssueActionRequest {
		@NotNull(message = "action is required")
		private String action; // VERIFY

		@Valid
		@JsonProperty("media_urls")
		private List<MediaRequest> mediaUrls;
	}
}
