package org.smalltech.hashtaglocal_backend.model.response;

import lombok.Builder;
import lombok.Data;
import org.smalltech.hashtaglocal_backend.model.Issue;

@Data
@Builder
public class IssueResponseData {
	private Issue issue;
}
