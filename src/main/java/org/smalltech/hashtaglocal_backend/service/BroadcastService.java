package org.smalltech.hashtaglocal_backend.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.Builder;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.smalltech.hashtaglocal_backend.entity.NotificationLogEntity;
import org.smalltech.hashtaglocal_backend.infra.notification.FcmSender;
import org.smalltech.hashtaglocal_backend.infra.notification.FcmSender.MulticastResult;
import org.smalltech.hashtaglocal_backend.model.NotificationSource;
import org.smalltech.hashtaglocal_backend.model.NotificationType;
import org.smalltech.hashtaglocal_backend.model.request.SendNotificationRequest.NotificationBody;
import org.smalltech.hashtaglocal_backend.repository.NotificationLogRepository;
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
  private final NotificationLogRepository notificationLogRepository;
  private final FcmSender fcmSender;
  private final ObjectMapper objectMapper;

  public NotificationResult sendNotification(NotificationBody notification) {
    if (!"BROADCAST".equalsIgnoreCase(notification.getType())) {
      throw new IllegalArgumentException(
          "Unsupported notification type: " + notification.getType());
    }

    String title = notification.getPayload().getTitle();
    String body = notification.getPayload().getBody();

    List<String> tokens = userAuthSessionRepository.findAllActiveNotificationTokens();
    Map<String, String> payload = Map.of("type", "BROADCAST");

    NotificationLogEntity logEntry =
        notificationLogRepository.save(
            NotificationLogEntity.builder()
                .source(NotificationSource.ADMIN)
                .type(NotificationType.BROADCAST)
                .title(title)
                .body(body)
                .payload(toJson(payload))
                .build());

    // FCM data = business payload + notificationLogId so the device can report opens back
    Map<String, String> fcmData = new HashMap<>(payload);
    fcmData.put("notificationLogId", logEntry.getId().toString());

    int totalSuccess = 0;
    for (int i = 0; i < tokens.size(); i += FCM_BATCH_SIZE) {
      List<String> batch = tokens.subList(i, Math.min(i + FCM_BATCH_SIZE, tokens.size()));
      MulticastResult result =
          fcmSender.sendMulticast(batch, title, body, fcmData, "broadcast_admin");
      result.staleTokens().forEach(userAuthSessionRepository::clearNotificationToken);
      totalSuccess += result.successCount();
    }

    log.info("Broadcast sent: {} recipients, {} FCM accepted", tokens.size(), totalSuccess);

    logEntry.setRecipientCount(tokens.size());
    logEntry.setSuccessCount(totalSuccess);
    notificationLogRepository.save(logEntry);

    return NotificationResult.builder().notificationDelivered(totalSuccess).build();
  }

  private String toJson(Map<String, String> map) {
    try {
      return objectMapper.writeValueAsString(map);
    } catch (JsonProcessingException e) {
      return "{}";
    }
  }

  @Data
  @Builder
  public static class NotificationResult {
    private int notificationDelivered;
  }
}
