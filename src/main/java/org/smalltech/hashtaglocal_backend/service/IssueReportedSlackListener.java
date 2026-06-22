package org.smalltech.hashtaglocal_backend.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.smalltech.hashtaglocal_backend.entity.IssueEntity;
import org.smalltech.hashtaglocal_backend.entity.Locality;
import org.smalltech.hashtaglocal_backend.event.IssueReportedEvent;
import org.smalltech.hashtaglocal_backend.infra.notification.SlackNotifier;
import org.smalltech.hashtaglocal_backend.repository.IssueRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/** Alerts the ops Slack channel whenever a user submits a new issue report. */
@Component
@RequiredArgsConstructor
@Slf4j
public class IssueReportedSlackListener {

  private final IssueRepository issueRepository;
  private final SlackNotifier slackNotifier;

  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
  @Transactional(propagation = Propagation.REQUIRES_NEW, readOnly = true)
  public void onIssueReported(IssueReportedEvent event) {
    IssueEntity issue = issueRepository.findById(event.issueId()).orElse(null);
    if (issue == null) {
      log.warn("Issue {} not found — skipping Slack alert", event.issueId());
      return;
    }

    String reporter = issue.getUserEntity() != null ? issue.getUserEntity().getUsername() : "unknown";
    Locality locality = issue.getLocation() != null ? issue.getLocation().getLocality() : null;
    String location = locality != null ? "#" + locality.getHashtag() : "unknown location";
    String description =
        issue.getDescription() != null && !issue.getDescription().isBlank()
            ? issue.getDescription()
            : "(no description)";

    String text =
        String.format(
            ":rotating_light: *New issue reported* (#%d)\n*Type:* %s\n*Reporter:* %s\n*Location:*"
                + " %s\n*Description:* %s",
            issue.getId(), issue.getType(), reporter, location, description);

    slackNotifier.send(text);
  }
}
