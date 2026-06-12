package org.smalltech.hashtaglocal_backend.job;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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

  @Value("${bulletin.weather.cron:0 0 8 * * *}")
  private String scheduleExpression;

  @Scheduled(cron = "${bulletin.weather.cron:0 0 8 * * *}")
  public void run() {
    log.info("Bulletin weather job started (cron={})", scheduleExpression);
    bulletinGenerationService.generateForAllUserLocalities();
  }
}
