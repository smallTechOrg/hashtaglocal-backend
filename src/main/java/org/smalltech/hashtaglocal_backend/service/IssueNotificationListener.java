package org.smalltech.hashtaglocal_backend.service;

import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.smalltech.hashtaglocal_backend.entity.DeviceTokenEntity;
import org.smalltech.hashtaglocal_backend.entity.UserEntity;
import org.smalltech.hashtaglocal_backend.event.IssueStatusChangedEvent;
import org.smalltech.hashtaglocal_backend.infra.notification.FcmSender;
import org.smalltech.hashtaglocal_backend.model.IssueStatusModel;
import org.smalltech.hashtaglocal_backend.repository.DeviceTokenRepository;
import org.smalltech.hashtaglocal_backend.repository.IssueActionRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
@RequiredArgsConstructor
@Slf4j
public class IssueNotificationListener {

  private final IssueActionRepository issueActionRepository;
  private final DeviceTokenRepository deviceTokenRepository;
  private final FcmSender fcmSender;

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
        deviceTokenRepository.findAllByUserId(reporter.getId()).stream()
            .map(DeviceTokenEntity::getToken)
            .toList();

    if (tokens.isEmpty()) {
      return;
    }

    Map<String, String> data =
        Map.of(
            "type", "ISSUE_UPDATE",
            "issueId", issueId.toString(),
            "status", "OPEN",
            "event", "STATUS_CHANGE");

    List<String> stale =
        fcmSender.sendMulticast(tokens, "Issue is live", "Your report has been approved and is now open.", data);

    stale.forEach(deviceTokenRepository::deleteByToken);
  }
}
