package org.smalltech.hashtaglocal_backend.service;

import org.smalltech.hashtaglocal_backend.model.AiCategory;
import org.smalltech.hashtaglocal_backend.model.AiVerdict;

/**
 * Pluggable AI moderation classifier. v1 implementation calls Gemini over REST; the interface keeps
 * the provider swappable. See FEED_DESIGN.md §8.
 */
public interface FeedModerationClient {

  /**
   * Classify a post's text (and optional link metadata). Implementations should be resilient: on
   * any failure they may throw, and the caller will fail safe to human review.
   */
  ModerationResult classify(String text, String linkUrl, String linkTitle);

  /** Structured verdict. */
  record ModerationResult(
      AiVerdict verdict, AiCategory category, double confidence, String reason, String model) {}
}
