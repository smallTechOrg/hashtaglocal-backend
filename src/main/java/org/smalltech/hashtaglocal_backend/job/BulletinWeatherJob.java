package org.smalltech.hashtaglocal_backend.job;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.smalltech.hashtaglocal_backend.infra.notification.SlackNotifier;
import org.smalltech.hashtaglocal_backend.service.BulletinGenerationService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Daily 8:00 AM bulletin run: weather (+ Groq summary + feed post) for every locality that saved
 * users belong to. A locality first seen today is picked up automatically tomorrow — nothing is
 * generated on-demand when a user opens the app.
 */
@Component
@RequiredArgsConstructor
@Slf4j
@ConditionalOnProperty(name = "bulletin.enabled", havingValue = "true", matchIfMissing = true)
public class BulletinWeatherJob {

  private final BulletinGenerationService bulletinGenerationService;
  private final SlackNotifier slackNotifier;

  @Value("${bulletin.weather.cron:0 0 8 * * *}")
  private String scheduleExpression;

  @Scheduled(cron = "${bulletin.weather.cron:0 0 8 * * *}", zone = "Asia/Kolkata")
  public void run() {
    log.info("Bulletin weather job started (cron={})", scheduleExpression);
    try {
      BulletinGenerationService.GenerationResult result =
          bulletinGenerationService.generateForAllUserLocalities();
      if (result.getFailed() > 0) {
        slackNotifier.send(
            String.format(
                ":warning: Bulletin weather job (%s): %d/%d localities failed (%d generated, %d"
                    + " skipped)",
                result.getDate(),
                result.getFailed(),
                result.getTotalLocalities(),
                result.getGenerated(),
                result.getSkipped()));
      }
    } catch (Exception e) {
      log.error("Bulletin weather job failed", e);
      slackNotifier.send(":x: Bulletin weather job failed: " + e.getMessage());
      throw e;
    }
  }
}
