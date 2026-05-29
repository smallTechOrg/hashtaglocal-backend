package org.smalltech.hashtaglocal_backend.model;

/**
 * The kind of a feed post. Decides which fields of {@code feed_post_content} (and which {@code
 * data} JSONB keys) are meaningful. See FEED_DESIGN.md §3.
 */
public enum FeedPostKind {
  TEXT,
  MEDIA,
  LINK,
  ISSUE_REF,
  EVENT_REF,
  BULLETIN,
  POLL,
  QUIZ
}
