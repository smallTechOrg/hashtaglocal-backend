package org.smalltech.hashtaglocal_backend.service;

import java.util.List;
import lombok.RequiredArgsConstructor;
import org.smalltech.hashtaglocal_backend.mapper.IssueViewMapper;
import org.smalltech.hashtaglocal_backend.model.Issue;
import org.smalltech.hashtaglocal_backend.model.NewAPIResponse;
import org.smalltech.hashtaglocal_backend.model.response.IssueListResponseData;
import org.smalltech.hashtaglocal_backend.model.response.IssueResponseData;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class IssueHomeService {

  private final IssueHomeQueryService issueHomeQueryService;
  private final IssueViewMapper issueViewMapper;

  public NewAPIResponse<IssueListResponseData> getHome(String localityHashtag) {
    var entities = issueHomeQueryService.findRecentIssues(localityHashtag);

    List<Issue> issues =
        entities.stream().map(issueViewMapper::map).map(IssueResponseData::getIssue).toList();

    return NewAPIResponse.<IssueListResponseData>builder()
        .data(IssueListResponseData.builder().issues(issues).build())
        .build();
  }

  public NewAPIResponse<IssueListResponseData> getNearby(double lat, double lng) {
    double radiusMeters = 5000.0;

    var entities = issueHomeQueryService.findNearbyIssues(lat, lng, radiusMeters);

    List<Issue> issues =
        entities.stream().map(issueViewMapper::map).map(IssueResponseData::getIssue).toList();

    return NewAPIResponse.<IssueListResponseData>builder()
        .data(IssueListResponseData.builder().issues(issues).build())
        .build();
  }
}
