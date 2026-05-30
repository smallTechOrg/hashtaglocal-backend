package org.smalltech.hashtaglocal_backend.model.request;

import lombok.Data;

/** Body for admin approve/hide actions. {@code note} is the audit reason. */
@Data
public class AdminModerationActionRequest {
  private String note;
}
