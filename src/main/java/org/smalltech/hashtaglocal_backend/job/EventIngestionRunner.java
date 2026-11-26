package org.smalltech.hashtaglocal_backend.job;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

/**
 * Starts an event ingestion run in the background, for {@code POST /admin/event-sources/run}. The
 * run takes the same {@link JobLock} as {@code POST /jobs/event-ingestion}, so it never overlaps a
 * scheduled run.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class EventIngestionRunner {

  static final String JOB_NAME = "event-ingestion";

  private final JobLock jobLock;
  private final EventIngestionCronJob eventIngestionCronJob;

  @Async
  public void runAsync() {
    try {
      if (!jobLock.runExclusive(JOB_NAME, eventIngestionCronJob::run)) {
        log.info("[JOBS] {} already running; manual run skipped", JOB_NAME);
      }
    } catch (Exception e) {
      log.error("[JOBS] manual {} failed", JOB_NAME, e);
    }
  }
}
