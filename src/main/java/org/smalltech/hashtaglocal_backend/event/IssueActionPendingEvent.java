package org.smalltech.hashtaglocal_backend.event;

import org.smalltech.hashtaglocal_backend.model.IssueActionModel;

/**
 * Published whenever an issue action (REPORT, VERIFY, or RESOLVE) lands in the admin review queue
 * (approvalStatus PENDING) — i.e. anything that shows up in the ops pending-actions list. Used to
 * alert ops (e.g. via Slack) that something needs review.
 */
public record IssueActionPendingEvent(Long issueId, IssueActionModel actionType, Long actorUserId) {}
