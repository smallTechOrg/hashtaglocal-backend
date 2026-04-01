package org.smalltech.hashtaglocal_backend.service;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.smalltech.hashtaglocal_backend.entity.IssueActionEntity;
import org.smalltech.hashtaglocal_backend.entity.IssueEntity;
import org.smalltech.hashtaglocal_backend.mapper.IssueViewMapper;
import org.smalltech.hashtaglocal_backend.model.GovPortalData;
import org.smalltech.hashtaglocal_backend.model.IssueActionApprovalStatus;
import org.smalltech.hashtaglocal_backend.model.IssueActionModel;
import org.smalltech.hashtaglocal_backend.model.IssueStatusModel;
import org.smalltech.hashtaglocal_backend.model.IssueStory;
import org.smalltech.hashtaglocal_backend.model.TimelineEvent;
import org.smalltech.hashtaglocal_backend.model.response.IssueResponseData;
import org.smalltech.hashtaglocal_backend.repository.IssueActionRepository;
import org.smalltech.hashtaglocal_backend.repository.IssueRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class IssueStoryService {

  private final IssueRepository issueRepository;
  private final IssueActionRepository issueActionRepository;
  private final IssueViewMapper issueViewMapper;

  public List<IssueStory> getStories(String localityHashtag, int limit) {
    LocalDateTime startDate = LocalDateTime.now().minusYears(2);
    List<IssueStatusModel> statuses = List.of(IssueStatusModel.RESOLVED);

    List<IssueEntity> resolvedIssues;
    if (localityHashtag != null && !localityHashtag.isBlank()) {
      resolvedIssues =
          issueRepository.findByStatusInAndCreatedAtAfterAndLocalityHashtagOrderByCreatedAtDesc(
              statuses, startDate, localityHashtag);
    } else {
      resolvedIssues =
          issueRepository.findByStatusInAndCreatedAtAfterOrderByCreatedAtDesc(statuses, startDate);
    }

    return resolvedIssues.stream().limit(limit).map(this::buildStory).toList();
  }

  private IssueStory buildStory(IssueEntity issueEntity) {
    IssueResponseData responseData = issueViewMapper.map(issueEntity);
    var issue = responseData.getIssue();

    List<TimelineEvent> timeline = new ArrayList<>();

    // 1. REPORTED — always present
    timeline.add(
        TimelineEvent.builder()
            .event("REPORTED")
            .timestamp(issueEntity.getCreatedAt())
            .details("Issue reported by " + issue.getUser().getUsername())
            .build());

    // 2. VERIFIED — first approved VERIFY action
    List<IssueActionEntity> verifyActions =
        issueActionRepository.findByIssueEntityAndActionAndApprovalStatus(
            issueEntity, IssueActionModel.VERIFY, IssueActionApprovalStatus.APPROVED);
    if (!verifyActions.isEmpty()) {
      IssueActionEntity firstVerify = verifyActions.get(0);
      LocalDateTime verifiedAt =
          firstVerify.getApprovedAt() != null
              ? firstVerify.getApprovedAt()
              : firstVerify.getCreatedAt();
      timeline.add(
          TimelineEvent.builder()
              .event("VERIFIED")
              .timestamp(verifiedAt)
              .details("Verified by community")
              .build());
    }

    // 3. PORTAL_SUBMITTED — from gov portal data
    List<GovPortalData> portalData = issue.getGovPortalData();
    if (portalData != null && !portalData.isEmpty()) {
      GovPortalData portal = portalData.get(0);
      String details =
          "Submitted to " + portal.getPortalName() + " (ID: " + portal.getTrackingId() + ")";
      timeline.add(
          TimelineEvent.builder()
              .event("PORTAL_SUBMITTED")
              .timestamp(portal.getCreatedAt())
              .details(details)
              .build());
    }

    // 4. RESOLVED — approved RESOLVE action
    List<IssueActionEntity> resolveActions =
        issueActionRepository.findByIssueEntityAndActionAndApprovalStatus(
            issueEntity, IssueActionModel.RESOLVE, IssueActionApprovalStatus.APPROVED);
    LocalDateTime resolvedAt = issueEntity.getUpdatedAt();
    if (!resolveActions.isEmpty()) {
      IssueActionEntity resolveAction = resolveActions.get(0);
      resolvedAt =
          resolveAction.getApprovedAt() != null
              ? resolveAction.getApprovedAt()
              : resolveAction.getCreatedAt();
    }
    timeline.add(
        TimelineEvent.builder()
            .event("RESOLVED")
            .timestamp(resolvedAt)
            .details("Issue resolved")
            .build());

    int resolutionDays = (int) ChronoUnit.DAYS.between(issueEntity.getCreatedAt(), resolvedAt);

    return IssueStory.builder()
        .issue(issue)
        .timeline(timeline)
        .resolutionDays(Math.max(resolutionDays, 1))
        .build();
  }
}
