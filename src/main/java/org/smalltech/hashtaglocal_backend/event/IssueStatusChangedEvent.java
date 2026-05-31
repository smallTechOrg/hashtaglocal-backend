package org.smalltech.hashtaglocal_backend.event;

import org.smalltech.hashtaglocal_backend.model.IssueStatusModel;

/**
 * Published when an issue's status changes via admin review. The feed module listens for this to
 * keep ISSUE_REF feed posts in sync with the issue lifecycle, so the public feed only ever shows
 * issues that are {@code OPEN} (approved) or {@code RESOLVED}:
 *
 * <ul>
 *   <li>{@code OPEN} (REPORT approved) → create an ISSUE_REF post if none exists.
 *   <li>{@code RESOLVED} (RESOLVE approved) → post again to announce the resolution.
 *   <li>{@code REJECTED} / {@code ONHOLD} → hide any ISSUE_REF posts for the issue.
 * </ul>
 *
 * <p>This replaces the old creation-time auto-post trigger, which posted issues while they were
 * still {@code ONHOLD} awaiting approval. See FEED_DESIGN.md §1.2 / §10.
 */
public record IssueStatusChangedEvent(Long issueId, IssueStatusModel newStatus) {}
