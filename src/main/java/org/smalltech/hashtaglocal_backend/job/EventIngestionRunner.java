package org.smalltech.hashtaglocal_backend.job;

import java.sql.SQLException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Runs event ingestion now, for {@code POST /admin/event-sources/run}, inside the caller's request.
 * It is not run in the background: on Cloud Run with request-based billing a container gets almost
 * no CPU once the response is sent, so background work stalls. The run takes the same {@link
 * JobLock} as {@code POST /jobs/event-ingestion}, so it never overlaps a scheduled run.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class EventIngestionRunner {

  static final String JOB_NAME = "event-ingestion";

  private final JobLock jobLock;
  private final EventIngestionCronJob eventIngestionCronJob;

  /** Runs ingestion and returns when it is done. False when a run was already in progress. */
  public boolean runNow() {
    boolean ran;
    try {
      ran = jobLock.runExclusive(JOB_NAME, eventIngestionCronJob::run);
    } catch (SQLException e) {
      throw new IllegalStateException("could not take the " + JOB_NAME + " job lock", e);
    }
    if (!ran) {
      log.info("[JOBS] {} already running; manual run skipped", JOB_NAME);
    }
    return ran;
  }
}
