package org.smalltech.hashtaglocal_backend.service;

import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.smalltech.hashtaglocal_backend.entity.FeedPostContentEntity;
import org.smalltech.hashtaglocal_backend.entity.IssueEntity;
import org.smalltech.hashtaglocal_backend.entity.Locality;
import org.smalltech.hashtaglocal_backend.model.FeedPostKind;
import org.smalltech.hashtaglocal_backend.repository.IssueRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Backfills ISSUE_REF feed posts for issues that were reported before the feed feature existed (so
 * they never got the auto-post). Idempotent: each run only processes issues that still lack an
 * ISSUE_REF post, so it can be re-triggered safely. Posts are backdated to the issue's original
 * {@code createdAt} so the timeline stays chronological. See FEED_DESIGN.md §1.2.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class FeedBackfillService {

  private static final int BATCH_SIZE = 100;

  private final IssueRepository issueRepository;
  private final FeedService feedService;

  /** Self lookup (via provider, no constructor cycle) so processBatch runs through the tx proxy. */
  private final org.springframework.beans.factory.ObjectProvider<FeedBackfillService> selfProvider;

  /** Result of a backfill run. */
  public record BackfillResult(int created, int skipped, int batches) {}

  /**
   * Backfill up to {@code maxIssues} issue-refs (0 = no cap). Processes oldest-first in committed
   * batches, so an interruption keeps prior batches and a rerun resumes from where it stopped.
   */
  public BackfillResult backfillIssueRefs(int maxIssues) {
    int created = 0;
    int skipped = 0;
    int batches = 0;

    while (maxIssues <= 0 || created + skipped < maxIssues) {
      int remaining =
          maxIssues <= 0 ? BATCH_SIZE : Math.min(BATCH_SIZE, maxIssues - created - skipped);
      if (remaining <= 0) break;

      List<IssueEntity> batch =
          issueRepository.findIssuesWithoutFeedRef(PageRequest.of(0, remaining));
      if (batch.isEmpty()) break;

      int[] tally = selfProvider.getObject().processBatch(batch);
      created += tally[0];
      skipped += tally[1];
      batches++;
      log.info("Feed backfill batch {}: created={}, skipped={}", batches, tally[0], tally[1]);

      // A full batch that produced no new posts (all skipped) means we can't make progress —
      // every remaining candidate is unpersistable; stop to avoid an infinite loop.
      if (tally[0] == 0) break;
    }

    log.info(
        "Feed backfill complete: created={}, skipped={}, batches={}", created, skipped, batches);
    return new BackfillResult(created, skipped, batches);
  }

  /**
   * Process one batch in its own transaction. Returns {@code [created, skipped]}. Each issue is
   * guarded individually so one bad row doesn't sink the batch.
   */
  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public int[] processBatch(List<IssueEntity> batch) {
    int created = 0;
    int skipped = 0;
    for (IssueEntity issue : batch) {
      Locality locality = issue.getLocation() != null ? issue.getLocation().getLocality() : null;
      if (locality == null) {
        skipped++;
        continue;
      }
      try {
        LocalDateTime when =
            issue.getCreatedAt() != null ? issue.getCreatedAt() : LocalDateTime.now();
        FeedPostContentEntity content = FeedPostContentEntity.builder().issue(issue).build();
        feedService.createBackfilledSystemPost(locality, FeedPostKind.ISSUE_REF, content, when);
        created++;
      } catch (RuntimeException e) {
        log.warn("Backfill skipped issue {}: {}", issue.getId(), e.toString());
        skipped++;
      }
    }
    return new int[] {created, skipped};
  }
}
