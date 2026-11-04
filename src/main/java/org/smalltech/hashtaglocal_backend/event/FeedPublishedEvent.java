package org.smalltech.hashtaglocal_backend.event;

/**
 * Fired when a chat post goes live, so the locality's users get a push notification. It only
 * carries the post id; the listener loads the post itself because it runs later on another thread.
 */
public record FeedPublishedEvent(Long feedPostId) {}
