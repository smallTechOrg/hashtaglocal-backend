package org.smalltech.hashtaglocal_backend.event;

/**
 * Published when a new issue is created. The feed module listens for this to auto-post an {@code
 * ISSUE_REF} to the issue's locality, keeping the issue flow unaware of the feed. See
 * FEED_DESIGN.md §1.2 / §10.
 */
public record IssueCreatedEvent(Long issueId) {}
