package org.smalltech.hashtaglocal_backend.model.response;

import java.time.LocalDateTime;
import lombok.Builder;
import lombok.Data;

/**
 * Created as the API response payload returned to the app after a user submits an account deletion
 * request, so the app can display the current status and the 24-hour deletion deadline.
 */
@Data
@Builder
public class AccountDeletionRequestResponseData {
  /** Current queue state shown to the app after the user confirms deletion. */
  private String status;

  /** Timestamp when the app user initiated deletion. */
  private LocalDateTime requestedAt;

  /** Manual deletion deadline communicated to the user. */
  private LocalDateTime scheduledDeletionAt;
}
