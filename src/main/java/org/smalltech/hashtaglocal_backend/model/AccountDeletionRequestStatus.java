package org.smalltech.hashtaglocal_backend.model;

/**
 * Created to represent the three lifecycle states of an account deletion request. PENDING –
 * awaiting manual admin action; COMPLETED – account has been deleted; CANCELLED – request was
 * withdrawn before deletion was carried out.
 */
public enum AccountDeletionRequestStatus {
  PENDING,
  COMPLETED,
  CANCELLED
}
