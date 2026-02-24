package org.smalltech.hashtaglocal_backend.service;

import org.smalltech.hashtaglocal_backend.model.request.IssueReportRequest;

public interface IssueReportService {
	Long createIssue(Long userId, IssueReportRequest request);
}
