package org.smalltech.hashtaglocal_backend.model;

/**
 * Review lifecycle of a quiz generated for a locality (#local Quiz Automation design).
 *
 * <ul>
 *   <li>{@code PENDING} – generated, waiting for an admin to approve or reject it.
 *   <li>{@code QUEUED} – approved; eligible to be picked by the 8 AM bulletin job.
 *   <li>{@code REJECTED} – rejected by an admin, with a {@code rejectionReason}.
 *   <li>{@code PUBLISHED} – attached to a day's bulletin by the 8 AM job (or entered manually).
 * </ul>
 */
public enum QuizStatus {
  PENDING,
  QUEUED,
  REJECTED,
  PUBLISHED
}
