package org.smalltech.hashtaglocal_backend.service;

import java.util.List;
import lombok.RequiredArgsConstructor;
import org.smalltech.hashtaglocal_backend.mapper.IssueViewMapper;
import org.smalltech.hashtaglocal_backend.model.APIResponse;
import org.smalltech.hashtaglocal_backend.model.Issue;
import org.smalltech.hashtaglocal_backend.model.ResponseData;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class IssueHomeAssembler {

	private final IssueHomeQueryService issueHomeQueryService;
	private final IssueViewMapper issueViewMapper;

	public APIResponse getHome(String localityHashtag) {
		var entities = issueHomeQueryService.findRecentIssues(localityHashtag);

		List<Issue> issues = entities.stream().map(issueViewMapper::map).toList();

		return APIResponse.builder().data(ResponseData.builder().issues(issues).build()).build();
	}

	public APIResponse getNearby(double lat, double lng) {
		double radiusMeters = 5000.0;

		var entities = issueHomeQueryService.findNearbyIssues(lat, lng, radiusMeters);

		List<Issue> issues = entities.stream().map(issueViewMapper::map).toList();

		return APIResponse.builder().data(ResponseData.builder().issues(issues).build()).build();
	}
}
