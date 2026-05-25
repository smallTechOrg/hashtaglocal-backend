package org.smalltech.hashtaglocal_backend.model;

/**
 * Approval state for a scraped event.
 *
 * <p>All newly ingested events start as {@link #PENDING}. An admin must explicitly approve them via
 * the ops portal before they appear on the public {@code GET /api/v1/events} endpoint.
 */
public enum EventApprovalStatus {
  PENDING,
  APPROVED,
  REJECTED
}
