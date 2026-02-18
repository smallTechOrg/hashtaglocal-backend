package org.smalltech.hashtaglocal_backend.service;

import java.util.List;
import lombok.RequiredArgsConstructor;
import org.smalltech.hashtaglocal_backend.config.CustomProperties;
import org.smalltech.hashtaglocal_backend.error.ApiException;
import org.smalltech.hashtaglocal_backend.error.ErrorCode;
import org.smalltech.hashtaglocal_backend.error.ErrorType;
import org.smalltech.hashtaglocal_backend.mapper.IssueViewMapper;
import org.smalltech.hashtaglocal_backend.model.APIResponse;
import org.smalltech.hashtaglocal_backend.model.Issue;
import org.smalltech.hashtaglocal_backend.model.ResponseData;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class IssueHomeService {

	private final CustomProperties customProperties;
	private final IssueHomeQueryService issueHomeQueryService;
	private final IssueViewMapper issueViewMapper;

	public APIResponse getHome(String localityHashtag) {
		var entities = issueHomeQueryService.findRecentIssues(localityHashtag);

		List<Issue> issues = entities.stream().map(issueViewMapper::map).toList();

		return APIResponse.builder().data(ResponseData.builder().issues(issues).build()).build();
	}

	public APIResponse getNearby(double lat, double lng) {

		// Validation at service layer
		if (lat < -90 || lat > 90 || lng < -180 || lng > 180) {
			throw new ApiException(HttpStatus.BAD_REQUEST, ErrorType.VALIDATION, ErrorCode.VALIDATION_FAILED,
					"Invalid latitude or longitude values");
		}
		System.out.println("VALIDATION CHECK lat=" + lat + ", lng=" + lng);

		double radiusMeters = customProperties.getGeo().getViewRadiusMeters();

		var entities = issueHomeQueryService.findNearbyIssues(lat, lng, radiusMeters);

		List<Issue> issues = entities.stream().map(issueViewMapper::map).toList();

		return APIResponse.builder().data(ResponseData.builder().issues(issues).build()).build();
	}
}
