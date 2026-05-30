package org.smalltech.hashtaglocal_backend.model;

/**
 * Moderation category the AI assigned to a post. {@code NONE} is used for clean ({@code ALLOW})
 * posts. The exact taxonomy is still being finalised with product (FEED_DESIGN.md §11); this is the
 * working set.
 */
public enum AiCategory {
  NONE,
  SPAM,
  HATE,
  NSFW,
  HARASSMENT,
  VIOLENCE,
  MISINFORMATION,
  OFF_TOPIC,
  OTHER
}
