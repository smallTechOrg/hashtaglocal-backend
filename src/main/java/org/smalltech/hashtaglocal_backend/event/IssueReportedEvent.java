package org.smalltech.hashtaglocal_backend.event;

/**
 * Published right after a user submits a new issue report, while it is still {@code ONHOLD}
 * awaiting admin review. Used to alert ops (e.g. via Slack) of incoming reports.
 */
public record IssueReportedEvent(Long issueId) {}
