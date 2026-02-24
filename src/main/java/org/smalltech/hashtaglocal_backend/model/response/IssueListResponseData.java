package org.smalltech.hashtaglocal_backend.model.response;

import java.util.List;
import lombok.Builder;
import lombok.Data;
import org.smalltech.hashtaglocal_backend.model.Issue;

@Data
@Builder
public class IssueListResponseData {
	private List<Issue> issues;
}
