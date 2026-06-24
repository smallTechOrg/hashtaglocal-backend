package org.smalltech.hashtaglocal_backend.job;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.smalltech.hashtaglocal_backend.service.QuizGenerationService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Weekly Sunday 7 AM IST job that generates quizzes for all saved-user localities for the upcoming
 * Monday–Sunday. Mirrors the pattern of {@link BulletinWeatherJob}.
 */
@Component
@RequiredArgsConstructor
@Slf4j
@ConditionalOnProperty(
    name = "quiz.generation.enabled",
    havingValue = "true",
    matchIfMissing = true)
public class QuizGenerationJob {

  private final QuizGenerationService quizGenerationService;

  @Value("${quiz.generation.cron:0 0 7 * * SUN}")
  private String scheduleExpression;

  @Scheduled(cron = "${quiz.generation.cron:0 0 7 * * SUN}", zone = "Asia/Kolkata")
  public void run() {
    log.info("Quiz generation job started (cron={})", scheduleExpression);
    quizGenerationService.generateWeeklyQuizzes();
  }
}
