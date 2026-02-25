package org.smalltech.hashtaglocal_backend.model;

public enum MediaStatusModel {
  ONHOLD,
  APPROVED,
  /** The media was submitted as part of a VERIFY/RESOLVE action that an admin rejected. */
  REJECTED
}
