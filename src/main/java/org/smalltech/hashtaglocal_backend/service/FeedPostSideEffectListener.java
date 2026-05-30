package org.smalltech.hashtaglocal_backend.service;

import lombok.RequiredArgsConstructor;
import org.smalltech.hashtaglocal_backend.event.FeedPostCreatedEvent;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Runs a feed post's async side-effects (link scrape, then AI moderation) AFTER the create
 * transaction commits, so the post row is always visible to the worker. Scrape runs before
 * moderation so moderation sees the scraped title and the two never race to write the same row. See
 * FEED_DESIGN.md §6 / §8.
 */
@Component
@RequiredArgsConstructor
public class FeedPostSideEffectListener {

  private final LinkScrapeService linkScrapeService;
  private final FeedModerationService feedModerationService;

  @Async
  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
  public void onPostCreated(FeedPostCreatedEvent event) {
    if (event.needsScrape()) {
      // Synchronous within this async listener thread: completes (and commits) before moderation,
      // so moderation reads the scraped title and writes occur strictly in sequence.
      linkScrapeService.scrape(event.feedPostId());
    }
    if (event.needsModeration()) {
      feedModerationService.moderate(event.feedPostId());
    }
  }
}
