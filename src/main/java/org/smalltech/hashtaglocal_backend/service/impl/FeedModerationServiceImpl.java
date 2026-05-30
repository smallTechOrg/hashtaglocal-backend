package org.smalltech.hashtaglocal_backend.service.impl;

import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.smalltech.hashtaglocal_backend.entity.FeedModerationEntity;
import org.smalltech.hashtaglocal_backend.entity.FeedPostContentEntity;
import org.smalltech.hashtaglocal_backend.entity.FeedPostEntity;
import org.smalltech.hashtaglocal_backend.model.AdminModerationAction;
import org.smalltech.hashtaglocal_backend.model.AiCategory;
import org.smalltech.hashtaglocal_backend.model.AiVerdict;
import org.smalltech.hashtaglocal_backend.model.FeedPostStatus;
import org.smalltech.hashtaglocal_backend.repository.FeedPostRepository;
import org.smalltech.hashtaglocal_backend.service.FeedModerationClient;
import org.smalltech.hashtaglocal_backend.service.FeedModerationService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Runs AI moderation for feed posts and transitions their status. Fail-safe: any AI error/timeout
 * moves the post to {@code FLAGGED} (human review), never auto-publishes. See FEED_DESIGN.md §8.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class FeedModerationServiceImpl implements FeedModerationService {

  private final FeedPostRepository feedPostRepository;
  private final FeedModerationClient moderationClient;

  /**
   * Self lookup (via provider to avoid a constructor cycle) so the sweeper dispatches each post
   * through the transactional proxy rather than a plain {@code this} call.
   */
  private final org.springframework.beans.factory.ObjectProvider<FeedModerationService>
      selfProvider;

  @Value("${feed.moderation.enabled:true}")
  private boolean enabled;

  @Override
  @Transactional
  public void moderate(Long feedPostId) {
    FeedPostEntity post = feedPostRepository.findById(feedPostId).orElse(null);
    if (post == null || post.getStatus() != FeedPostStatus.PENDING_AI) {
      return; // already handled, or gone
    }

    if (!enabled) {
      // Moderation disabled (e.g. local dev without a key) — flag for human review rather than
      // auto-publishing.
      transition(post, FeedPostStatus.FLAGGED, null);
      return;
    }

    FeedPostContentEntity content = post.getContent();
    String text = content != null ? content.getText() : null;
    String url = content != null ? content.getUrl() : null;
    String title = content != null ? content.getTitle() : null;

    try {
      FeedModerationClient.ModerationResult r = moderationClient.classify(text, url, title);
      FeedPostStatus newStatus =
          switch (r.verdict()) {
            case ALLOW -> FeedPostStatus.PUBLISHED;
            case BLOCK -> FeedPostStatus.AI_BLOCKED;
            case UNCERTAIN -> FeedPostStatus.FLAGGED;
          };
      transition(post, newStatus, r);
    } catch (Exception e) {
      log.warn(
          "AI moderation failed for feed post {} — flagging for review: {}",
          feedPostId,
          e.toString());
      transition(post, FeedPostStatus.FLAGGED, null);
    }
  }

  private void transition(
      FeedPostEntity post, FeedPostStatus newStatus, FeedModerationClient.ModerationResult r) {
    post.setStatus(newStatus);
    if (newStatus == FeedPostStatus.PUBLISHED && post.getPublishedAt() == null) {
      post.setPublishedAt(LocalDateTime.now());
    }

    FeedModerationEntity mod = post.getModeration();
    if (mod == null) {
      mod =
          FeedModerationEntity.builder().post(post).adminAction(AdminModerationAction.NONE).build();
      post.setModeration(mod);
    }
    if (r != null) {
      mod.setAiVerdict(r.verdict());
      mod.setAiCategory(r.category());
      mod.setAiConfidence(r.confidence());
      mod.setAiReason(r.reason());
      mod.setAiModel(r.model());
    } else {
      mod.setAiVerdict(AiVerdict.UNCERTAIN);
      mod.setAiCategory(AiCategory.NONE);
      mod.setAiReason("Moderation unavailable — flagged for human review");
    }
    mod.setEvaluatedAt(LocalDateTime.now());
    feedPostRepository.save(post);
  }

  /**
   * Retry sweeper: re-process posts stuck in PENDING_AI (e.g. after a crash). Dispatches each via
   * the async entry point so every post gets its own transaction.
   */
  @Scheduled(fixedDelayString = "${feed.moderation.sweeper-delay-ms:60000}")
  @Transactional(readOnly = true)
  public void sweepStuck() {
    List<FeedPostEntity> stuck =
        feedPostRepository.findByStatus(FeedPostStatus.PENDING_AI, PageRequest.of(0, 20));
    FeedModerationService self = selfProvider.getObject();
    for (FeedPostEntity p : stuck) {
      // Dispatch through the proxy so each post is moderated in its own transaction.
      self.moderate(p.getId());
    }
  }
}
