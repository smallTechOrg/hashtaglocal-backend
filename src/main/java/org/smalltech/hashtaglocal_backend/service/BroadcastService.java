package org.smalltech.hashtaglocal_backend.service;

import java.util.List;
import java.util.Map;
import lombok.Builder;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.smalltech.hashtaglocal_backend.infra.notification.FcmSender;
import org.smalltech.hashtaglocal_backend.model.request.SendNotificationRequest.NotificationBody;
import org.smalltech.hashtaglocal_backend.repository.UserAuthSessionRepository;
import org.springframework.stereotype.Service;

/** Ops-portal admin-triggered push notifications. */
@Service
@RequiredArgsConstructor
@Slf4j
public class BroadcastService {

  // FCM rejects multicast calls with more than 500 tokens.
  private static final int FCM_BATCH_SIZE = 500;

  private final UserAuthSessionRepository userAuthSessionRepository;
  private final FcmSender fcmSender;

  public NotificationResult sendNotification(NotificationBody notification) {
    if (!"BROADCAST".equalsIgnoreCase(notification.getType())) {
      throw new IllegalArgumentException(
          "Unsupported notification type: " + notification.getType());
    }

    String title = notification.getPayload().getTitle();
    String body = notification.getPayload().getBody();

    List<String> tokens = userAuthSessionRepository.findAllActiveNotificationTokens();
    Map<String, String> data = Map.of("type", "BROADCAST");

    int staleCount = 0;
    for (int i = 0; i < tokens.size(); i += FCM_BATCH_SIZE) {
      List<String> batch = tokens.subList(i, Math.min(i + FCM_BATCH_SIZE, tokens.size()));
      List<String> stale = fcmSender.sendMulticast(batch, title, body, data);
      stale.forEach(userAuthSessionRepository::clearNotificationToken);
      staleCount += stale.size();
    }

    log.info("Broadcast sent: {} recipients, {} stale tokens dropped", tokens.size(), staleCount);

    return NotificationResult.builder().notificationDelivered(tokens.size() - staleCount).build();
  }

  @Data
  @Builder
  public static class NotificationResult {
    private int notificationDelivered;
  }
}
