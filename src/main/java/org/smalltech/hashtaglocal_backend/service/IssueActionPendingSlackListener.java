package org.smalltech.hashtaglocal_backend.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.smalltech.hashtaglocal_backend.entity.IssueEntity;
import org.smalltech.hashtaglocal_backend.entity.Locality;
import org.smalltech.hashtaglocal_backend.entity.UserEntity;
import org.smalltech.hashtaglocal_backend.event.IssueActionPendingEvent;
import org.smalltech.hashtaglocal_backend.infra.notification.SlackNotifier;
import org.smalltech.hashtaglocal_backend.model.IssueActionModel;
import org.smalltech.hashtaglocal_backend.repository.IssueRepository;
import org.smalltech.hashtaglocal_backend.repository.UserRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Alerts the ops Slack review-queue channel whenever any issue action (REPORT, VERIFY, or RESOLVE)
 * lands in the admin pending-approval queue — the single alert for "something needs review in the
 * backend."
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class IssueActionPendingSlackListener {

  private final IssueRepository issueRepository;
  private final UserRepository userRepository;
  private final SlackNotifier slackNotifier;

  @Value("${ops.base-url:https://local.smalltech.in}")
  private String opsBaseUrl;

  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
  @Transactional(propagation = Propagation.REQUIRES_NEW, readOnly = true)
  public void onIssueActionPending(IssueActionPendingEvent event) {
    IssueEntity issue = issueRepository.findById(event.issueId()).orElse(null);
    if (issue == null) {
      log.warn("Issue {} not found — skipping Slack review alert", event.issueId());
      return;
    }

    String actor =
        userRepository.findById(event.actorUserId()).map(UserEntity::getUsername).orElse("unknown");
    Locality locality = issue.getLocation() != null ? issue.getLocation().getLocality() : null;
    String location = locality != null ? "#" + locality.getHashtag() : "unknown location";
    String reviewLink = "Review in ops admin: " + opsBaseUrl + "/ops/review";

    String text;
    if (event.actionType() == IssueActionModel.REPORT) {
      text =
          String.format(
              ":rotating_light: *New issue reported — pending review* (#%d)\n*Reporter:* %s\n%s",
              issue.getId(), actor, reviewLink);
    } else {
      text =
          String.format(
              ":mag: *%s submitted — pending review* (issue #%d)\n*Type:* %s\n*By:* %s\n"
                  + "*Location:* %s\n%s",
              event.actionType(), issue.getId(), issue.getType(), actor, location, reviewLink);
    }

    slackNotifier.send(text);
  }
}
