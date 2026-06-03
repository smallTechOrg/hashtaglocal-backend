package org.smalltech.hashtaglocal_backend.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.smalltech.hashtaglocal_backend.entity.FeedPostContentEntity;
import org.smalltech.hashtaglocal_backend.entity.IssueEntity;
import org.smalltech.hashtaglocal_backend.entity.Locality;
import org.smalltech.hashtaglocal_backend.event.IssueStatusChangedEvent;
import org.smalltech.hashtaglocal_backend.model.FeedPostKind;
import org.smalltech.hashtaglocal_backend.model.IssueStatusModel;
import org.smalltech.hashtaglocal_backend.repository.FeedPostRepository;
import org.smalltech.hashtaglocal_backend.repository.IssueRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Keeps ISSUE_REF feed posts in sync with the issue lifecycle so the public feed only ever shows
 * issues that are {@code OPEN} (approved) or {@code RESOLVED}. Runs after the issue transaction
 * commits so it never references uncommitted state. See FEED_DESIGN.md §1.2.
 *
 * <ul>
 *   <li>{@code OPEN} — issue approved → post an ISSUE_REF (once; skips if one already exists).
 *   <li>{@code RESOLVED} — issue resolved → post again to announce the resolution.
 *   <li>{@code REJECTED} / {@code ONHOLD} — issue left the public states → hide its ISSUE_REF
 *       posts.
 * </ul>
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class FeedIssueRefListener {

  private final IssueRepository issueRepository;
  private final FeedPostRepository feedPostRepository;
  private final FeedService feedService;

  @org.springframework.beans.factory.annotation.Value("${feed.auto-issue-ref.enabled:true}")
  private boolean autoIssueRefEnabled;

  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public void onIssueStatusChanged(IssueStatusChangedEvent event) {
    if (!autoIssueRefEnabled) {
      return;
    }

    IssueStatusModel status = event.newStatus();

    // Leaving the publicly-visible states: hide any ISSUE_REF posts for this issue.
    if (status == IssueStatusModel.REJECTED || status == IssueStatusModel.ONHOLD) {
      int hidden = feedPostRepository.hideIssueRefPosts(event.issueId());
      if (hidden > 0) {
        log.info("Hid {} ISSUE_REF post(s) for issue {} ({})", hidden, event.issueId(), status);
      }
      return;
    }

    // Only OPEN (approved) and RESOLVED produce feed posts.
    if (status != IssueStatusModel.OPEN && status != IssueStatusModel.RESOLVED) {
      return;
    }

    IssueEntity issue = issueRepository.findById(event.issueId()).orElse(null);
    if (issue == null || issue.getLocation() == null) {
      return;
    }
    Locality locality = issue.getLocation().getLocality();
    if (locality == null) {
      log.debug("Issue {} has no locality — skipping auto feed post", event.issueId());
      return;
    }

    // On approval (OPEN) post once; if a post already exists (e.g. re-approval), don't duplicate.
    // On RESOLVED we always post again to announce the resolution.
    if (status == IssueStatusModel.OPEN
        && feedPostRepository.countIssueRefPosts(issue.getId()) > 0) {
      return;
    }

    FeedPostContentEntity content = FeedPostContentEntity.builder().issue(issue).build();
    feedService.createSystemPost(locality, FeedPostKind.ISSUE_REF, content);
    log.info(
        "Auto-posted ISSUE_REF for issue {} to #{} ({})",
        issue.getId(),
        locality.getHashtag(),
        status);
  }
}
