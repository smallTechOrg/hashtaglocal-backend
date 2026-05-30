package org.smalltech.hashtaglocal_backend.model;

/**
 * Lifecycle status of a feed post. The public timeline shows {@code PUBLISHED} only; everything
 * else is visible only to the author (their own pending/blocked posts) and admins. See
 * FEED_DESIGN.md §8.
 *
 * <ul>
 *   <li>{@code PENDING_AI} – created, awaiting AI moderation verdict.
 *   <li>{@code PUBLISHED} – live and publicly visible.
 *   <li>{@code FLAGGED} – AI was uncertain (or errored) → human review.
 *   <li>{@code AI_BLOCKED} – AI blocked it; an admin may still approve.
 *   <li>{@code ADMIN_HIDDEN} – an admin hid a previously live/approved post.
 * </ul>
 */
public enum FeedPostStatus {
  PENDING_AI,
  PUBLISHED,
  FLAGGED,
  AI_BLOCKED,
  ADMIN_HIDDEN
}
