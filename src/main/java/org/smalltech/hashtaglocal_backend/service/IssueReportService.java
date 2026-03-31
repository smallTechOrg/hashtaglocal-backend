package org.smalltech.hashtaglocal_backend.service;

import org.smalltech.hashtaglocal_backend.model.IssueActionResult;
import org.smalltech.hashtaglocal_backend.model.request.IssueReportRequest;

public interface IssueReportService {
  IssueActionResult createIssue(Long userId, IssueReportRequest request);
}
