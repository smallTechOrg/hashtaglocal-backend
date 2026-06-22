package org.smalltech.hashtaglocal_backend.job;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.smalltech.hashtaglocal_backend.infra.notification.SlackChannel;
import org.smalltech.hashtaglocal_backend.infra.notification.SlackNotifier;
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
  private final SlackNotifier slackNotifier;

  @Value("${portalissue.cron:0 */30 * * * *}")
  private String scheduleExpression;

  @Scheduled(cron = "${portalissue.cron:0 */30 * * * *}")
  public void run() {
    log.info("Portal issue tracking job started (cron={})", scheduleExpression);
    try {
      portalIssueTrackingService.runCycle();
    } catch (Exception e) {
      log.error("Portal issue tracking job failed", e);
      slackNotifier.send(
          SlackChannel.CRON, ":x: Portal issue tracking job failed: " + e.getMessage());
      throw e;
    }
  }
}
