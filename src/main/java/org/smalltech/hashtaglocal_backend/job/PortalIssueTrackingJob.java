package org.smalltech.hashtaglocal_backend.job;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.smalltech.hashtaglocal_backend.service.PortalIssueTrackingService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
@ConditionalOnProperty(name = "portalissue.enabled", havingValue = "true", matchIfMissing = true)
public class PortalIssueTrackingJob {

  private final PortalIssueTrackingService portalIssueTrackingService;

  @Value("${portalissue.fixed-delay-ms:0 */30 * * * *}")
  private String scheduleExpression;

  @Scheduled(cron = "${portalissue.fixed-delay-ms:0 */30 * * * *}")
  public void run() {
    log.info("Portal issue tracking job started (cron={})", scheduleExpression);
    portalIssueTrackingService.runCycle();
  }
}
