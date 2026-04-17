package org.smalltech.hashtaglocal_backend.service;

import java.util.Collections;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.smalltech.hashtaglocal_backend.dto.TrackIssueScrapeResponseDTO;
import org.smalltech.hashtaglocal_backend.entity.GovPortalEntity;
import org.smalltech.hashtaglocal_backend.repository.GovPortalRepository;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class PortalIssueTrackingService {

  private static final String TRACKABLE_STATUS = "OPEN";

  private final GovPortalRepository govPortalRepository;
  private final PortalIssueScrapeClient portalIssueScrapeClient;

  public void runCycle() {
    Set<Long> attemptedIds = new HashSet<>();
    int processedCount = 0;
    int failedCount = 0;

    while (true) {
      Optional<GovPortalEntity> nextIssue = findNextEligibleIssue(attemptedIds);
      if (nextIssue.isEmpty()) {
        break;
      }

      GovPortalEntity issue = nextIssue.get();
      attemptedIds.add(issue.getId());

      try {
        String portal = issue.getPortal() != null ? issue.getPortal().name() : null;
        String trackingId = issue.getTrackingId();

        TrackIssueScrapeResponseDTO response =
            portalIssueScrapeClient.trackIssue(portal, trackingId);
        applySuccessUpdate(issue, response);
        processedCount++;

        log.info(
            "Portal issue tracking success: portal={} trackingId={} status={}",
            portal,
            trackingId,
            response.getData().getStatus());
      } catch (Exception ex) {
        failedCount++;
        log.error(
            "Portal issue tracking failed: portal={} trackingId={} error={}",
            issue.getPortal(),
            issue.getTrackingId(),
            ex.getMessage());
      }
    }

    log.info(
        "Portal issue tracking cycle complete: successful={} failed={} attempted={}",
        processedCount,
        failedCount,
        attemptedIds.size());
  }

  private Optional<GovPortalEntity> findNextEligibleIssue(Set<Long> attemptedIds) {
    if (attemptedIds.isEmpty()) {
      return govPortalRepository.findFirstByStatusOrderByUpdatedAtAsc(TRACKABLE_STATUS);
    }

    return govPortalRepository.findFirstByStatusAndIdNotInOrderByUpdatedAtAsc(
        TRACKABLE_STATUS, attemptedIds);
  }

  private void applySuccessUpdate(GovPortalEntity issue, TrackIssueScrapeResponseDTO response) {
    TrackIssueScrapeResponseDTO.Data data = response.getData();
    if (data.getStatus() == null || data.getStatus().isBlank()) {
      throw new IllegalStateException("Portal scrape API response is missing data.status");
    }

    Map<String, Object> metaData =
        data.getMetaData() == null ? Collections.emptyMap() : data.getMetaData();

    issue.setStatus(data.getStatus());
    issue.setMetaData(metaData);
    govPortalRepository.save(issue);
  }
}
