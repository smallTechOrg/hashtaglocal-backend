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
import org.springframework.transaction.annotation.Transactional;

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

  @Transactional
  public NotificationResult sendNotification(NotificationBody notification) {
    String typeStr = notification.getType().toUpperCase();
    NotificationType type =
        switch (typeStr) {
          case "BROADCAST" -> NotificationType.BROADCAST;
          case "CHAT" -> NotificationType.CHAT;
          case "ISSUE_DETAIL" -> NotificationType.ISSUE_DETAIL;
          default ->
              throw new IllegalArgumentException("Unsupported notification type: " + typeStr);
        };

    String title = notification.getPayload().getTitle();
    String body = notification.getPayload().getBody();
    String issueId = notification.getPayload().getIssueId();

    if (type == NotificationType.ISSUE_DETAIL && (issueId == null || issueId.isBlank())) {
      throw new IllegalArgumentException("issueId is required for ISSUE_DETAIL notifications");
    }

    Map<String, String> payload = new HashMap<>();
    payload.put("type", type.name());
    if (type == NotificationType.ISSUE_DETAIL) {
      payload.put("issueId", issueId);
    }

    List<String> tokens = userAuthSessionRepository.findAllActiveNotificationTokens();

    NotificationLogEntity logEntry =
        notificationLogRepository.save(
            NotificationLogEntity.builder()
                .source(NotificationSource.ADMIN)
                .type(type)
                .title(title)
                .body(body)
                .payload(toJson(payload))
                .build());

    // FCM data = business payload + notificationLogId so the device can report opens back
    Map<String, String> fcmData = new HashMap<>(payload);
    fcmData.put("notificationLogId", logEntry.getId().toString());

    String fcmTag = type.name().toLowerCase() + "_admin";
    int totalSuccess = 0;
    for (int i = 0; i < tokens.size(); i += FCM_BATCH_SIZE) {
      List<String> batch = tokens.subList(i, Math.min(i + FCM_BATCH_SIZE, tokens.size()));
      MulticastResult result = fcmSender.sendMulticast(batch, title, body, fcmData, fcmTag);
      result.staleTokens().forEach(userAuthSessionRepository::clearNotificationToken);
      totalSuccess += result.successCount();
    }

    log.info("{} sent: {} recipients, {} FCM accepted", type, tokens.size(), totalSuccess);


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
