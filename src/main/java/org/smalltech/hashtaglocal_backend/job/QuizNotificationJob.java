package org.smalltech.hashtaglocal_backend.job;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.smalltech.hashtaglocal_backend.model.NotificationType;
import org.smalltech.hashtaglocal_backend.service.BroadcastService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Daily reminder that today's quiz is live, pushed to every active device (type=CHAT, lands on the
 * chat page — same route as any other CHAT notification). Runs independently of {@link
 * BulletinWeatherJob}: content doesn't reference a specific locality/bulletin, so it doesn't wait
 * on that day's generation to finish.
 */
@Component
@RequiredArgsConstructor
@Slf4j
@ConditionalOnProperty(
    name = "quiz.notification.enabled",
    havingValue = "true",
    matchIfMissing = true)
public class QuizNotificationJob {

  private final BroadcastService broadcastService;

  @Value("${quiz.notification.cron:0 30 8 * * *}")
  private String scheduleExpression;

  @Value("${quiz.notification.title}")
  private String title;

  @Value("${quiz.notification.body}")
  private String body;

  @Scheduled(cron = "${quiz.notification.cron:0 30 8 * * *}", zone = "Asia/Kolkata")
  public void run() {
    log.info("Quiz notification job started (cron={})", scheduleExpression);
    broadcastService.sendSystemNotification(NotificationType.CHAT, "BULLETIN", title, body);
  }
}
