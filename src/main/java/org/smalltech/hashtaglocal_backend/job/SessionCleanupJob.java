package org.smalltech.hashtaglocal_backend.job;

import jakarta.transaction.Transactional;
import java.time.Instant;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.smalltech.hashtaglocal_backend.infra.notification.SlackChannel;
import org.smalltech.hashtaglocal_backend.infra.notification.SlackNotifier;
import org.smalltech.hashtaglocal_backend.repository.UserAuthSessionRepository;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Nightly job that removes stale rows from user_auth_sessions so the table does not grow
 * unboundedly.
 *
 * <p>Two passes:
 *
 * <ol>
 *   <li>Delete any session whose refresh token expired more than 7 days ago. These rows can never
 *       be used to authenticate again.
 *   <li>Delete inactive sessions (isActive = false) that have not been touched in 30 days. Catches
 *       rows deactivated by account deletion.
 * </ol>
 *
 * <p>Runs at 03:00 server time every day. Override via {@code AUTH_SESSION_CLEANUP_CRON} env var;
 * set to {@code -} to disable.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class SessionCleanupJob {

  private static final long EXPIRED_GRACE_SECONDS = 7L * 24 * 3600; // 7 days
  private static final int INACTIVE_GRACE_DAYS = 30;

  private final UserAuthSessionRepository userAuthSessionRepository;
  private final SlackNotifier slackNotifier;

  @Scheduled(cron = "${auth.session.cleanup-cron:0 0 3 * * *}")
  @Transactional
  public void run() {
    log.info("[SessionCleanup] Starting nightly session cleanup");

    try {
      long expiredCutoff = Instant.now().getEpochSecond() - EXPIRED_GRACE_SECONDS;
      int expiredDeleted =
          userAuthSessionRepository.deleteByRefreshTokenExpiredBefore(expiredCutoff);

      LocalDateTime inactiveCutoff = LocalDateTime.now().minusDays(INACTIVE_GRACE_DAYS);
      int inactiveDeleted = userAuthSessionRepository.deleteInactiveSessionsOlderThan(inactiveCutoff);

      log.info(
          "[SessionCleanup] Done — deleted {} expired-token sessions, {} stale inactive sessions",
          expiredDeleted,
          inactiveDeleted);
    } catch (Exception e) {
      log.error("[SessionCleanup] Job failed", e);
      slackNotifier.send(SlackChannel.CRON, ":x: Session cleanup job failed: " + e.getMessage());
      throw e;
    }
  }
}
