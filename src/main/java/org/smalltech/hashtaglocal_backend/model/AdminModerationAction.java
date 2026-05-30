package org.smalltech.hashtaglocal_backend.model;

/** The human override an admin applied on top of the AI verdict. See FEED_DESIGN.md §8. */
public enum AdminModerationAction {
  /** No admin has acted; the AI verdict stands. */
  NONE,
  /** Admin overrode an AI block (or flag) and published the post. */
  APPROVED,
  /** Admin hid a post (whether AI-approved or live). */
  HIDDEN
}
