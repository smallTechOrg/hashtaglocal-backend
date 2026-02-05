package org.smalltech.hashtaglocal_backend.model.request;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import lombok.Data;

@Data
public class IssueVerifyRequest {
	@JsonProperty("issue_action")
	private IssueActionRequest issueAction;

	@Data
	public static class IssueActionRequest {
		private String action; // VERIFY
		@JsonProperty("media_urls")
		private List<MediaRequest> mediaUrls;
	}
}
