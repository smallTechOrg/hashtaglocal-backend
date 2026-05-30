package org.smalltech.hashtaglocal_backend.model.request;

import lombok.Data;

/** Body for admin pin/unpin. */
@Data
public class AdminPinRequest {
  private boolean pinned;
}
