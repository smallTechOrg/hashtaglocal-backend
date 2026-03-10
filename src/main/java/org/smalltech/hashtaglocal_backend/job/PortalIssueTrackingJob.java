package org.smalltech.hashtaglocal_backend.job;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.smalltech.hashtaglocal_backend.service.PortalIssueTrackingService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
@ConditionalOnProperty(
    name = "portal-issue-tracking.enabled",
    havingValue = "true",
    matchIfMissing = true)
public class PortalIssueTrackingJob {

  private final PortalIssueTrackingService portalIssueTrackingService;

  @Scheduled(fixedDelayString = "${portal-issue-tracking.fixed-delay-ms:1800000}")
  public void run() {
    log.info("Portal issue tracking job started");
    portalIssueTrackingService.runCycle();
  }
}
