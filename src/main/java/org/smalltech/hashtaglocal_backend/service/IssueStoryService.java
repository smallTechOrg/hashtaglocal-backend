package org.smalltech.hashtaglocal_backend.service;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.smalltech.hashtaglocal_backend.entity.GovPortalEntity;
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
import org.smalltech.hashtaglocal_backend.repository.GovPortalRepository;
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
  private final GovPortalRepository govPortalRepository;
  private final IssueViewMapper issueViewMapper;

  public List<IssueStory> getStories(String localityHashtag) {
    LocalDateTime startDate = LocalDateTime.now().minusYears(2);
    List<IssueStatusModel> statuses = List.of(IssueStatusModel.RESOLVED, IssueStatusModel.OPEN);

    List<IssueEntity> candidates;
    if (localityHashtag != null && !localityHashtag.isBlank()) {
      candidates =
          issueRepository.findByStatusInAndCreatedAtAfterAndLocalityHashtagOrderByCreatedAtDesc(
              statuses, startDate, localityHashtag);
    } else {
      candidates =
          issueRepository.findByStatusInAndCreatedAtAfterOrderByCreatedAtDesc(statuses, startDate);
    }

    // Pre-load portal data and verification flags for all candidates in two bulk queries
    Set<Long> candidateIds =
        candidates.stream().map(IssueEntity::getId).collect(Collectors.toSet());
    List<GovPortalEntity> allPortalEntities = govPortalRepository.findByIssueIds(candidateIds);
    Map<Long, List<GovPortalEntity>> portalByIssueId =
        allPortalEntities.stream().collect(Collectors.groupingBy(p -> p.getIssueEntity().getId()));
    Set<Long> verifiedIssueIds = issueActionRepository.findVerifiedIssueIds(candidateIds);

    // Classify into priority buckets
    // Excluded: issues with 0 verifications AND no portal entry
    List<IssueEntity> bucket1Resolved = new ArrayList<>(); // Resolved on our platform
    List<IssueEntity> bucket2PortalResolved =
        new ArrayList<>(); // Open here, resolved on gov portal
    List<IssueEntity> bucket3PortalOpen = new ArrayList<>(); // Open here AND on gov portal
    List<IssueEntity> bucket4Verified = new ArrayList<>(); // Open, verified, no portal (max 10)

    for (IssueEntity issue : candidates) {
      if (issue.getStatus() == IssueStatusModel.RESOLVED) {
        bucket1Resolved.add(issue);
        continue;
      }

      List<GovPortalEntity> portalEntries = portalByIssueId.get(issue.getId());
      boolean hasPortal = portalEntries != null && !portalEntries.isEmpty();
      boolean hasVerifications = verifiedIssueIds.contains(issue.getId());

      // Skip issues with neither portal entry nor verifications
      if (!hasPortal && !hasVerifications) {
        continue;
      }

      if (hasPortal) {
        boolean portalResolved =
            portalEntries.stream()
                .anyMatch(
                    p -> {
                      String s = p.getStatus();
                      if (s == null) return false;
                      String lower = s.toLowerCase();
                      return lower.contains("resolved")
                          || lower.contains("closed")
                          || lower.contains("completed");
                    });
        if (portalResolved) {
          bucket2PortalResolved.add(issue);
        } else {
          bucket3PortalOpen.add(issue);
        }
      } else if (bucket4Verified.size() < 10) {
        bucket4Verified.add(issue);
      }
    }

    List<IssueEntity> ordered = new ArrayList<>();
    ordered.addAll(bucket1Resolved);
    ordered.addAll(bucket2PortalResolved);
    ordered.addAll(bucket3PortalOpen);
    ordered.addAll(bucket4Verified);

    return ordered.stream().map(this::buildStory).toList();
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

    // 2. VERIFIED — earliest VERIFY action (use createdAt = when user submitted, not approvedAt)
    List<IssueActionEntity> verifyActions =
        issueActionRepository.findByIssueEntityAndActionAndApprovalStatus(
            issueEntity, IssueActionModel.VERIFY, IssueActionApprovalStatus.APPROVED);
    if (!verifyActions.isEmpty()) {
      IssueActionEntity firstVerify =
          verifyActions.stream().min((a, b) -> a.getCreatedAt().compareTo(b.getCreatedAt())).get();
      timeline.add(
          TimelineEvent.builder()
              .event("VERIFIED")
              .timestamp(firstVerify.getCreatedAt())
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

    // 4. RESOLVED — use createdAt of RESOLVE action (when user submitted, not admin approval)
    Integer resolutionDays = null;
    if (issueEntity.getStatus() == IssueStatusModel.RESOLVED) {
      List<IssueActionEntity> resolveActions =
          issueActionRepository.findByIssueEntityAndActionAndApprovalStatus(
              issueEntity, IssueActionModel.RESOLVE, IssueActionApprovalStatus.APPROVED);
      LocalDateTime resolvedAt = issueEntity.getUpdatedAt();
      if (!resolveActions.isEmpty()) {
        resolvedAt =
            resolveActions.stream()
                .min((a, b) -> a.getCreatedAt().compareTo(b.getCreatedAt()))
                .get()
                .getCreatedAt();
      }
      timeline.add(
          TimelineEvent.builder()
              .event("RESOLVED")
              .timestamp(resolvedAt)
              .details("Issue resolved")
              .build());

      resolutionDays =
          Math.max((int) ChronoUnit.DAYS.between(issueEntity.getCreatedAt(), resolvedAt), 1);
    }

    int daysSinceReported =
        Math.max((int) ChronoUnit.DAYS.between(issueEntity.getCreatedAt(), LocalDateTime.now()), 1);

    return IssueStory.builder()
        .issue(issue)
        .timeline(timeline)
        .resolutionDays(resolutionDays)
        .daysSinceReported(daysSinceReported)
        .build();
  }
}
