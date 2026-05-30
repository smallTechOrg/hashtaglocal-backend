package org.smalltech.hashtaglocal_backend.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.smalltech.hashtaglocal_backend.entity.FeedPostContentEntity;
import org.smalltech.hashtaglocal_backend.entity.IssueEntity;
import org.smalltech.hashtaglocal_backend.entity.Locality;
import org.smalltech.hashtaglocal_backend.event.IssueCreatedEvent;
import org.smalltech.hashtaglocal_backend.model.FeedPostKind;
import org.smalltech.hashtaglocal_backend.repository.IssueRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Auto-posts an {@code ISSUE_REF} to the feed when a new issue is created. Runs after the issue
 * transaction commits so it never references an uncommitted issue. See FEED_DESIGN.md §1.2.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class FeedIssueRefListener {

  private final IssueRepository issueRepository;
  private final FeedService feedService;

  @org.springframework.beans.factory.annotation.Value("${feed.auto-issue-ref.enabled:true}")
  private boolean autoIssueRefEnabled;

  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public void onIssueCreated(IssueCreatedEvent event) {
    if (!autoIssueRefEnabled) {
      return;
    }
    IssueEntity issue = issueRepository.findById(event.issueId()).orElse(null);
    if (issue == null || issue.getLocation() == null) {
      return;
    }
    Locality locality = issue.getLocation().getLocality();
    if (locality == null) {
      log.debug("Issue {} has no locality yet — skipping auto feed post", event.issueId());
      return;
    }

    FeedPostContentEntity content = FeedPostContentEntity.builder().issue(issue).build();
    feedService.createSystemPost(locality, FeedPostKind.ISSUE_REF, content);
    log.info("Auto-posted ISSUE_REF for issue {} to #{}", issue.getId(), locality.getHashtag());
  }
}
