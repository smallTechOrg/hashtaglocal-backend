package org.smalltech.hashtaglocal_backend.job;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.smalltech.hashtaglocal_backend.infra.notification.SlackNotifier;
import org.smalltech.hashtaglocal_backend.model.IssueActionModel;
import org.smalltech.hashtaglocal_backend.repository.IssueActionRepository;
import org.smalltech.hashtaglocal_backend.repository.IssueRepository;
import org.smalltech.hashtaglocal_backend.repository.NotificationLogRepository;
import org.smalltech.hashtaglocal_backend.repository.QuizAttemptRepository;
import org.smalltech.hashtaglocal_backend.repository.UserRepository;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Posts a weekly ops summary to Slack every Monday morning, covering the week that just ended (last
 * Monday 00:00 through yesterday/Sunday 23:59, Asia/Kolkata).
 */
@Component
@RequiredArgsConstructor
@Slf4j
@ConditionalOnProperty(name = "digest.weekly.enabled", havingValue = "true", matchIfMissing = true)
public class WeeklyDigestJob {

  private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("MMM d");

  private final UserRepository userRepository;
  private final IssueRepository issueRepository;
  private final IssueActionRepository issueActionRepository;
  private final QuizAttemptRepository quizAttemptRepository;
  private final NotificationLogRepository notificationLogRepository;
  private final SlackNotifier slackNotifier;

  @Scheduled(cron = "${digest.weekly.cron:0 0 9 * * MON}", zone = "Asia/Kolkata")
  public void run() {
    LocalDate today = LocalDate.now(ZoneId.of("Asia/Kolkata"));
    LocalDate weekStart = today.minusDays(7);
    LocalDate weekEndExclusive = today;
    LocalDateTime start = weekStart.atStartOfDay();
    LocalDateTime end = weekEndExclusive.atStartOfDay();

    log.info("Weekly digest job started for week {} – {}", weekStart, weekStart.plusDays(6));

    long newUsers = userRepository.countByCreatedAtBetween(start, end);
    long issuesReported = issueRepository.countByCreatedAtBetween(start, end);
    long uniqueReporters = issueRepository.countDistinctReportersByCreatedAtBetween(start, end);
    long issuesVerified =
        issueActionRepository.countDistinctIssuesByApprovedActionBetween(
            IssueActionModel.VERIFY, start, end);
    long issuesResolved =
        issueActionRepository.countDistinctIssuesByApprovedActionBetween(
            IssueActionModel.RESOLVE, start, end);
    long quizAttempts = quizAttemptRepository.countByCreatedAtBetween(start, end);
    long uniqueQuizParticipants =
        quizAttemptRepository.countDistinctUsersByCreatedAtBetween(start, end);
    long notificationsSent = notificationLogRepository.countByCreatedAtBetween(start, end);

    String text =
        String.format(
            ":bar_chart: *Weekly Digest* — %s to %s\n"
                + "• New users: %d\n"
                + "• Issues reported: %d (by %d unique reporter%s)\n"
                + "• Issues verified: %d\n"
                + "• Issues resolved: %d\n"
                + "• Quiz attempts: %d (by %d unique participant%s)\n"
                + "• Notifications sent: %d",
            weekStart.format(DATE_FORMAT),
            weekStart.plusDays(6).format(DATE_FORMAT),
            newUsers,
            issuesReported,
            uniqueReporters,
            uniqueReporters == 1 ? "" : "s",
            issuesVerified,
            issuesResolved,
            quizAttempts,
            uniqueQuizParticipants,
            uniqueQuizParticipants == 1 ? "" : "s",
            notificationsSent);

    slackNotifier.send(text);
    log.info("Weekly digest sent");
  }
}
