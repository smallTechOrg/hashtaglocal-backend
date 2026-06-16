package org.smalltech.hashtaglocal_backend.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.smalltech.hashtaglocal_backend.model.NewAPIResponse;
import org.smalltech.hashtaglocal_backend.model.request.SendNotificationRequest;
import org.smalltech.hashtaglocal_backend.service.BroadcastService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Ops-portal admin-triggered push notifications. All routes are under {@code /admin} and require
 * the ADMIN role.
 */
@RestController
@RequestMapping("/admin")
@Tag(name = "Admin — Notification", description = "Send a push notification to every user.")
@SecurityRequirement(name = "bearerAuth")
@RequiredArgsConstructor
public class BroadcastAdminController {

  private final BroadcastService broadcastService;

  @PostMapping("/notification")
  @Operation(
      summary = "Send a push notification",
      description =
          "Sends notification.payload's title/body to every user with an active device token."
              + " Only notification.type = BROADCAST is supported today. Fire-and-forget — no"
              + " history is stored.")
  public ResponseEntity<NewAPIResponse<BroadcastService.NotificationResult>> sendNotification(
      @Valid @RequestBody SendNotificationRequest request) {
    return ResponseEntity.ok(
        NewAPIResponse.<BroadcastService.NotificationResult>builder()
            .data(broadcastService.sendNotification(request.getNotification()))
            .build());
  }
}
