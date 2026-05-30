package org.smalltech.hashtaglocal_backend.service;

/**
 * Drives AI moderation of a feed post: calls the classifier, records the verdict on {@code
 * feed_moderation}, and transitions the post's status. See FEED_DESIGN.md §8.
 */
public interface FeedModerationService {

  /**
   * Moderate the post with the given id in its own transaction. The post must already be persisted
   * (and committed) in {@code PENDING_AI}. On AI error/timeout the post is moved to {@code FLAGGED}
   * (fail-safe — never auto-published). Call from an already-async / post-commit context.
   */
  void moderate(Long feedPostId);
}
