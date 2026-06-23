package org.smalltech.hashtaglocal_backend.model.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/** Envelope for admin-triggered push notifications. Supports BROADCAST, CHAT, ISSUE_DETAIL. */
@Data
public class SendNotificationRequest {

  @Valid @NotNull private NotificationBody notification;

  @Data
  public static class NotificationBody {
    @NotBlank private String type;
    @Valid @NotNull private NotificationPayload payload;
  }

  @Data
  public static class NotificationPayload {
    @NotBlank private String title;
    @NotBlank private String body;
    // Required when type = ISSUE_DETAIL; ignored for BROADCAST and CHAT
    private String issueId;
  }
}
