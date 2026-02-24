package org.smalltech.hashtaglocal_backend.model.response;

import java.util.List;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class IssueListResponseData {
	private List<IssueResponseData> issues;
}
