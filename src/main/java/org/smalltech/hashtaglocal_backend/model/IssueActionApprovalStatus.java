package org.smalltech.hashtaglocal_backend.model;

/**
 * Tracks the admin approval state of an {@link
 * org.smalltech.hashtaglocal_backend.entity.IssueActionEntity}.
 *
 * <ul>
 *   <li>{@code NOT_REQUIRED} – the action type does not require admin review (e.g. REJECT, UPDATE).
 *   <li>{@code PENDING} – the action has been submitted by a user and is awaiting admin review.
 *   <li>{@code APPROVED} – an admin has approved the action; status side-effects are applied.
 *   <li>{@code REJECTED} – an admin has rejected the action; any pending status changes are
 *       reverted.
 * </ul>
 */
public enum IssueActionApprovalStatus {
  NOT_REQUIRED,
  PENDING,
  APPROVED,
  REJECTED
}
