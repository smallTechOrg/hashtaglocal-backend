package org.smalltech.hashtaglocal_backend.job;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.smalltech.hashtaglocal_backend.service.BulletinNotificationService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Daily 8:30 AM bulletin push, after the 8:00 weather run: retries generation for any locality the
 * weather run missed, then notifies users of every locality whose bulletin is ready. Safe to re-run
 * — already-notified bulletins are skipped.
 */
@Component
@RequiredArgsConstructor
@Slf4j
@ConditionalOnProperty(name = "bulletin.enabled", havingValue = "true", matchIfMissing = true)
public class BulletinNotificationJob {

  private final BulletinNotificationService bulletinNotificationService;

  @Value("${bulletin.notification.cron:0 30 8 * * *}")
  private String scheduleExpression;

  @Scheduled(cron = "${bulletin.notification.cron:0 30 8 * * *}", zone = "Asia/Kolkata")
  public void run() {
    log.info("Bulletin notification job started (cron={})", scheduleExpression);
    bulletinNotificationService.runCycle();
  }
}
