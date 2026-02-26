package org.smalltech.hashtaglocal_backend.model.response;

import java.time.LocalDateTime;
import lombok.Builder;
import lombok.Data;

/**
 * Response payload for a single issue action returned by the admin queue endpoint.
 *
 * <p>Provides enough context for an admin to understand what action requires review without needing
 * to fetch the full issue.
 */
@Data
@Builder
public class IssueActionAdminResponseData {

  /** ID of the action record. Use this as the {@code actionId} in approve/reject calls. */
  private Long actionId;

  /** ID of the issue this action belongs to. */
  private Long issueId;

  /** ID of the user who submitted the action. Used to fetch user summary stats. */
  private Long submittedByUserId;

  /** Username of the user who submitted the action. */
  private String submittedByUsername;

  /** The type of action: REPORT, VERIFY, or RESOLVE. */
  private String action;

  /** Current approval status (always PENDING for items in the queue). */
  private String approvalStatus;

  /** When the action was submitted. */
  private LocalDateTime createdAt;
}
