package org.smalltech.hashtaglocal_backend.event;

/**
 * Published when a feed post row has been created. Async side-effects (link scrape, AI moderation)
 * are triggered by a listener AFTER the create transaction commits, so the worker thread never
 * reads the post before it is visible. See FEED_DESIGN.md §6 / §8.
 *
 * @param feedPostId the persisted post id
 * @param needsScrape true for LINK posts that need preview scraping
 * @param needsModeration true for user posts that must go through the AI gate
 */
public record FeedPostCreatedEvent(Long feedPostId, boolean needsScrape, boolean needsModeration) {}
