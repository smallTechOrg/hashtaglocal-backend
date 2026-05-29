package org.smalltech.hashtaglocal_backend.model;

/**
 * The AI moderation verdict for a feed post. Maps to a {@link FeedPostStatus} transition: {@code
 * ALLOW}→PUBLISHED, {@code BLOCK}→AI_BLOCKED, {@code UNCERTAIN}→FLAGGED. See FEED_DESIGN.md §8.
 */
public enum AiVerdict {
  ALLOW,
  BLOCK,
  UNCERTAIN
}
