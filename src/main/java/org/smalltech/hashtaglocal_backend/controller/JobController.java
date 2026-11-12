package org.smalltech.hashtaglocal_backend.controller;

import io.swagger.v3.oas.annotations.Hidden;
import java.sql.SQLException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Supplier;
import lombok.extern.slf4j.Slf4j;
import org.smalltech.hashtaglocal_backend.job.BulletinWeatherJob;
import org.smalltech.hashtaglocal_backend.job.EventIngestionCronJob;
import org.smalltech.hashtaglocal_backend.job.JobLock;
import org.smalltech.hashtaglocal_backend.job.PortalIssueTrackingJob;
import org.smalltech.hashtaglocal_backend.job.SessionCleanupJob;
import org.smalltech.hashtaglocal_backend.security.JobInvokerVerifier;
import org.smalltech.hashtaglocal_backend.service.impl.FeedModerationServiceImpl;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Runs the background jobs on request, for Cloud Scheduler.
 *
 * <p>On Cloud Run the in-process {@code @Scheduled} timer is off ({@code SCHEDULER_ENABLED=false})
 * and Cloud Scheduler calls {@code POST /jobs/{name}} on each job's cron instead, with an OIDC
 * token that {@link JobInvokerVerifier} checks. The job runs inside the request; {@link JobLock}
 * keeps two runs of the same job from overlapping.
 *
 * <p>Responses: 200 the run finished (or the job is switched off in this environment), 401 caller
 * not allowed, 404 unknown job, 409 already running, 500 the run failed, 503 database unreachable.
 */
@RestController
@RequestMapping("/jobs")
@Hidden
@Slf4j
public class JobController {

  private final JobInvokerVerifier verifier;
  private final JobLock lock;

  /** Job name to its task; the supplier answers null when the job is switched off here. */
  private final Map<String, Supplier<Runnable>> jobs = new LinkedHashMap<>();

  public JobController(
      JobInvokerVerifier verifier,
      JobLock lock,
      EventIngestionCronJob eventIngestion,
      ObjectProvider<BulletinWeatherJob> bulletinWeather,
      ObjectProvider<PortalIssueTrackingJob> portalIssueTracking,
      SessionCleanupJob sessionCleanup,
      ObjectProvider<FeedModerationServiceImpl> feedModeration) {
    this.verifier = verifier;
    this.lock = lock;
    jobs.put("event-ingestion", () -> eventIngestion::run);
    // Bulletin and portal jobs only exist when enabled (bulletin.enabled, portalissue.enabled).
    jobs.put("bulletin-weather", optional(bulletinWeather, BulletinWeatherJob::run));
    jobs.put("portal-issue-tracking", optional(portalIssueTracking, PortalIssueTrackingJob::run));
    jobs.put("session-cleanup", () -> sessionCleanup::run);
    jobs.put(
        "feed-moderation-sweep", optional(feedModeration, FeedModerationServiceImpl::sweepStuck));
  }

  @PostMapping("/{name}")
  public ResponseEntity<Map<String, Object>> run(
      @PathVariable String name,
      @RequestHeader(value = "Authorization", required = false) String authorization) {
    String caller;
    try {
      caller = verifier.verify(authorization);
    } catch (SecurityException e) {
      log.warn("[JOBS] {} refused: {}", name, e.getMessage());
      return reply(HttpStatus.UNAUTHORIZED, "error", "unauthorized");
    }

    Supplier<Runnable> job = jobs.get(name);
    if (job == null) {
      return reply(HttpStatus.NOT_FOUND, "error", "unknown job");
    }
    Runnable task = job.get();
    if (task == null) {
      log.info("[JOBS] {} is switched off in this environment; skipped", name);
      return reply(HttpStatus.OK, "status", "disabled");
    }

    log.info("[JOBS] {} requested by {}", name, caller);
    try {
      if (!lock.runExclusive(name, task)) {
        log.info("[JOBS] {} already running; skipped", name);
        return reply(HttpStatus.CONFLICT, "status", "skipped");
      }
    } catch (SQLException e) {
      log.error("[JOBS] {} could not reach the database: {}", name, e.getMessage());
      return reply(HttpStatus.SERVICE_UNAVAILABLE, "error", "database unavailable");
    } catch (Exception e) {
      log.error("[JOBS] {} failed", name, e);
      return reply(HttpStatus.INTERNAL_SERVER_ERROR, "error", "job failed");
    }
    log.info("[JOBS] {} finished", name);
    return reply(HttpStatus.OK, "status", "ok");
  }

  private static <T> Supplier<Runnable> optional(ObjectProvider<T> beans, Consumer<T> call) {
    return () -> {
      T bean = beans.getIfAvailable();
      return bean == null ? null : () -> call.accept(bean);
    };
  }

  private static ResponseEntity<Map<String, Object>> reply(HttpStatus status, String k, String v) {
    return ResponseEntity.status(status).body(Map.of(k, v));
  }
}
