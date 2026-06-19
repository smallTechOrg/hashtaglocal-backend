package org.smalltech.hashtaglocal_backend.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.smalltech.hashtaglocal_backend.entity.NotificationLogEntity;
import org.smalltech.hashtaglocal_backend.entity.UserEntity;
import org.smalltech.hashtaglocal_backend.event.IssueStatusChangedEvent;
import org.smalltech.hashtaglocal_backend.infra.notification.FcmSender;
import org.smalltech.hashtaglocal_backend.infra.notification.FcmSender.MulticastResult;
import org.smalltech.hashtaglocal_backend.model.IssueStatusModel;
import org.smalltech.hashtaglocal_backend.model.NotificationSource;
import org.smalltech.hashtaglocal_backend.model.NotificationType;
import org.smalltech.hashtaglocal_backend.repository.IssueActionRepository;
import org.smalltech.hashtaglocal_backend.repository.NotificationLogRepository;
import org.smalltech.hashtaglocal_backend.repository.UserAuthSessionRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
@RequiredArgsConstructor
@Slf4j
public class IssueNotificationListener {

  private static final String TITLE = "Issue is live";
  private static final String BODY = "Your report has been approved and is now open.";

  private final IssueActionRepository issueActionRepository;
  private final UserAuthSessionRepository userAuthSessionRepository;
  private final NotificationLogRepository notificationLogRepository;
  private final FcmSender fcmSender;
  private final ObjectMapper objectMapper;

  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public void onIssueStatusChanged(IssueStatusChangedEvent event) {
    if (event.newStatus() == IssueStatusModel.OPEN) {
      notifyReporterApproved(event.issueId());
    }
  }

  private void notifyReporterApproved(Long issueId) {
    UserEntity reporter = issueActionRepository.findReporterByIssueId(issueId).orElse(null);
    if (reporter == null) {
      log.warn("No reporter found for issue {} — skipping notification", issueId);
      return;
    }

    List<String> tokens =
        userAuthSessionRepository.findActiveNotificationTokensByUserId(reporter.getId());

    if (tokens.isEmpty()) {
      return;
    }

    Map<String, String> payload =
        Map.of(
            "type", "ISSUE_DETAIL",
            "issueId", issueId.toString(),
            "status", "OPEN",
            "event", "STATUS_CHANGE");

    NotificationLogEntity logEntry =
        notificationLogRepository.save(
            NotificationLogEntity.builder()
                .source(NotificationSource.SYSTEM)
                .sourceRefType("ISSUE_UPDATE")
                .sourceRefId(issueId)
                .type(NotificationType.ISSUE_DETAIL)
                .title(TITLE)
                .body(BODY)
                .payload(toJson(payload))
                .build());

    // FCM data = business payload + notificationLogId so the device can report opens back
    Map<String, String> fcmData = new HashMap<>(payload);
    fcmData.put("notificationLogId", logEntry.getId().toString());

    MulticastResult result =
        fcmSender.sendMulticast(tokens, TITLE, BODY, fcmData, "issue_detail_system");
    result.staleTokens().forEach(userAuthSessionRepository::clearNotificationToken);

    logEntry.setRecipientCount(tokens.size());
    logEntry.setSuccessCount(result.successCount());
    notificationLogRepository.save(logEntry);
  }

  private String toJson(Map<String, String> map) {
    try {
      return objectMapper.writeValueAsString(map);
    } catch (JsonProcessingException e) {
      return "{}";
    }
  }
}
