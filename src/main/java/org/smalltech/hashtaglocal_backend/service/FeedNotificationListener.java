package org.smalltech.hashtaglocal_backend.service;

import static org.smalltech.hashtaglocal_backend.config.NotificationConstants.FCM_BATCH_SIZE;
import static org.smalltech.hashtaglocal_backend.config.TimeConstants.APP_ZONE;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.smalltech.hashtaglocal_backend.entity.BulletinEntity;
import org.smalltech.hashtaglocal_backend.entity.FeedPostEntity;
import org.smalltech.hashtaglocal_backend.entity.IssueEntity;
import org.smalltech.hashtaglocal_backend.entity.NotificationLogEntity;
import org.smalltech.hashtaglocal_backend.entity.UserEntity;
import org.smalltech.hashtaglocal_backend.event.FeedPublishedEvent;
import org.smalltech.hashtaglocal_backend.infra.notification.FcmSender;
import org.smalltech.hashtaglocal_backend.infra.notification.FcmSender.MulticastResult;
import org.smalltech.hashtaglocal_backend.model.FeedPostKind;
import org.smalltech.hashtaglocal_backend.model.FeedPostStatus;
import org.smalltech.hashtaglocal_backend.model.IssueStatusModel;
import org.smalltech.hashtaglocal_backend.model.NotificationSource;
import org.smalltech.hashtaglocal_backend.model.NotificationSubType;
import org.smalltech.hashtaglocal_backend.model.NotificationType;
import org.smalltech.hashtaglocal_backend.model.SourceRefType;
import org.smalltech.hashtaglocal_backend.repository.BulletinRepository;
import org.smalltech.hashtaglocal_backend.repository.FeedPostRepository;
import org.smalltech.hashtaglocal_backend.repository.NotificationLogRepository;
import org.smalltech.hashtaglocal_backend.repository.UserAuthSessionRepository;
import org.smalltech.hashtaglocal_backend.util.NotificationUtils;
import org.smalltech.hashtaglocal_backend.util.NotificationUtils.PushMessage;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Sends a push to a locality's users when a chat post goes live: a "Daily Bulletin" push for
 * bulletins, an "issue opened/resolved" push for issue posts, and a normal chat push for the rest.
 * Each push is logged before sending, so it goes out at most once; the author is never notified.
 * The title, body and data of each push are built by {@link NotificationUtils}.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class FeedNotificationListener {

  private static final String ANALYTICS_LABEL = "bulletin_system";
  private static final String CHAT_ANALYTICS_LABEL = "chat_system";
  private static final String ISSUE_ANALYTICS_LABEL = "issue_update_system";

  private final FeedPostRepository feedPostRepository;
  private final BulletinRepository bulletinRepository;
  private final UserAuthSessionRepository userAuthSessionRepository;
  private final NotificationLogRepository notificationLogRepository;
  private final FcmSender fcmSender;
  private final ObjectMapper objectMapper;
  private final PlatformTransactionManager transactionManager;

  @Value("${bulletin.notification.enabled:false}")
  private boolean bulletinNotificationEnabled;

  @Value("${feed.notification.enabled:false}")
  private boolean chatNotificationEnabled;

  // fallbackExecution: the generation cron may publish outside a transaction.
  @Async
  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
  public void onFeedPublished(FeedPublishedEvent event) {
    try {
      notifyPost(event.feedPostId());
    } catch (Exception e) {
      log.error("Feed notification failed for post {}: {}", event.feedPostId(), e.getMessage(), e);
    }
  }

  /** Routes a just-published feed post to the bulletin push or the generic chat push. */
  void notifyPost(Long feedPostId) {
    FeedPostEntity post = feedPostRepository.findByIdForNotification(feedPostId).orElse(null);
    if (post == null) {
      // Should never happen: the event is only published after the post is committed.
      log.error("Feed post {} not found — NO notification sent; investigate", feedPostId);
      return;
    }
    // Hidden/deleted between publish and now (e.g. an issue rejected right after approval).
    if (post.getStatus() != FeedPostStatus.PUBLISHED) {
      log.info(
          "Feed post {} is {} — no longer public, no notification", feedPostId, post.getStatus());
      return;
    }

    if (post.getKind() == FeedPostKind.BULLETIN) {
      if (!bulletinNotificationEnabled) {
        return;
      }
      BulletinEntity bulletin = post.getContent() != null ? post.getContent().getBulletin() : null;
      if (bulletin == null) {
        log.error("BULLETIN post {} has no bulletin — NO notification sent", feedPostId);
        return;
      }
      notifyLocality(bulletin.getId());
      return;
    }

    if (!chatNotificationEnabled) {
      return;
    }
    if (post.getKind() == FeedPostKind.ISSUE_REF) {
      notifyIssuePost(post);
    } else {
      notifyChatPost(post);
    }
  }

  // ---------------------------------------------------------------------------------------------
  // Bulletin push
  // ---------------------------------------------------------------------------------------------

  void notifyLocality(Long bulletinId) {
    if (alreadyNotified(NotificationSubType.CHAT_BULLETIN, SourceRefType.BULLETIN, bulletinId)) {
      log.debug("Bulletin {} already notified — skipping", bulletinId);
      return;
    }

    BulletinEntity bulletin = bulletinRepository.findByIdWithLocality(bulletinId).orElse(null);
    if (bulletin == null) {
      // Should never happen: the event is only published after the bulletin and its post are saved.
      // Nothing is sent (there is nothing true to say); ERROR so it shows up when debugging.
      log.error("Bulletin {} not found — NO notification sent; investigate", bulletinId);
      return;
    }

    // Only today's bulletin is worth a push: a late repair run must not notify about an old one.
    if (!bulletin.getDate().equals(LocalDate.now(APP_ZONE))) {
      log.info(
          "Bulletin {} is dated {}, not today — no notification", bulletinId, bulletin.getDate());
      return;
    }

    Long localityId = bulletin.getLocality().getId();
    List<String> tokens =
        userAuthSessionRepository.findActiveNotificationTokensByLocalityId(localityId);
    if (tokens.isEmpty()) {
      return;
    }

    PushMessage message = NotificationUtils.buildBulletinMessage(bulletin);

    NotificationLogEntity claim =
        claim(SourceRefType.BULLETIN, bulletinId, NotificationSubType.CHAT_BULLETIN, message)
            .orElse(null);
    if (claim == null) {
      log.info("Bulletin {} already claimed by another run — nothing sent", bulletinId);
      return;
    }

    int delivered = send(tokens, message, claim.getId(), ANALYTICS_LABEL);
    recordCounts(claim, tokens.size(), delivered);
    log.info(
        "Bulletin {} notified locality {}: {} recipients, {} delivered",
        bulletinId,
        localityId,
        tokens.size(),
        delivered);
  }

  // ---------------------------------------------------------------------------------------------
  // Chat push (every non-bulletin post)
  // ---------------------------------------------------------------------------------------------

  void notifyChatPost(FeedPostEntity post) {
    deliver(
        SourceRefType.FEED_POST,
        post.getId(),
        NotificationSubType.CHAT_POST,
        post.getLocality().getId(),
        post.getAuthor(),
        NotificationUtils.buildChatMessage(post),
        CHAT_ANALYTICS_LABEL);
  }

  // ---------------------------------------------------------------------------------------------
  // Issue update push (ISSUE_REF posts)
  // ---------------------------------------------------------------------------------------------

  /**
   * "New issue in …" while the issue is open, "Issue resolved in …" once it is resolved. Logged
   * against the issue (not the post), so regenerating the issue's feed post never pushes again.
   */
  void notifyIssuePost(FeedPostEntity post) {
    IssueEntity issue = post.getContent() != null ? post.getContent().getIssue() : null;
    if (issue == null) {
      log.error("ISSUE_REF post {} has no issue — NO notification sent", post.getId());
      return;
    }
    boolean resolved = issue.getStatus() == IssueStatusModel.RESOLVED;
    deliver(
        SourceRefType.ISSUE,
        issue.getId(),
        resolved ? NotificationSubType.CHAT_ISSUE_RESOLVED : NotificationSubType.CHAT_ISSUE,
        post.getLocality().getId(),
        issueExcludedUser(post, issue, resolved),
        NotificationUtils.buildIssueMessage(post, issue, resolved),
        ISSUE_ANALYTICS_LABEL);
  }

  /**
   * Who is left out of an issue push. A post someone shared by hand leaves out only that person. A
   * system post on approval leaves out the reporter, who already got "Issue is live"
   * (IssueNotificationListener). Nobody else tells the reporter about a resolution, so that push
   * goes to them too.
   */
  private static UserEntity issueExcludedUser(
      FeedPostEntity post, IssueEntity issue, boolean resolved) {
    if (post.getAuthor() != null) {
      return post.getAuthor();
    }
    return resolved ? null : issue.getUserEntity();
  }

  // ---------------------------------------------------------------------------------------------
  // Shared: dedupe, recipients, claim, send
  // ---------------------------------------------------------------------------------------------

  /** Dedupe → recipients → claim → send → record, for one chat or issue push. */
  private void deliver(
      SourceRefType sourceRefType,
      Long sourceRefId,
      NotificationSubType subType,
      Long localityId,
      UserEntity excludedUser,
      PushMessage message,
      String analyticsLabel) {
    if (alreadyNotified(subType, sourceRefType, sourceRefId)) {
      log.debug("{} {} {} already notified — skipping", subType, sourceRefType, sourceRefId);
      return;
    }

    List<String> tokens = recipients(localityId, excludedUser);
    if (tokens.isEmpty()) {
      return;
    }

    NotificationLogEntity claim = claim(sourceRefType, sourceRefId, subType, message).orElse(null);
    if (claim == null) {
      log.info(
          "{} {} {} already claimed by another run — nothing sent",
          subType,
          sourceRefType,
          sourceRefId);
      return;
    }

    int delivered = send(tokens, message, claim.getId(), analyticsLabel);
    recordCounts(claim, tokens.size(), delivered);
    log.info(
        "{} {} {} notified locality {}: {} recipients, {} delivered",
        subType,
        sourceRefType,
        sourceRefId,
        localityId,
        tokens.size(),
        delivered);
  }

  /** The locality's devices, minus those of {@code excludedUser} (the post/update's author). */
  private List<String> recipients(Long localityId, UserEntity excludedUser) {
    if (excludedUser == null) {
      return userAuthSessionRepository.findActiveNotificationTokensByLocalityId(localityId);
    }
    return userAuthSessionRepository.findActiveNotificationTokensByLocalityIdExcludingUser(
        localityId, excludedUser.getId());
  }

  /** The duplicate-send guard, in the shape of the documented access pattern. */
  private boolean alreadyNotified(
      NotificationSubType subType, SourceRefType sourceRefType, Long sourceRefId) {
    return notificationLogRepository.existsByTypeAndSubTypeAndSourceRefTypeAndSourceRefId(
        NotificationType.AUTO_CHAT, subType, sourceRefType, sourceRefId);
  }

  /**
   * Inserts the log row in its own committed transaction BEFORE anything is sent; the unique index
   * rejects a parallel duplicate here. Empty means another run already claimed this source.
   */
  private Optional<NotificationLogEntity> claim(
      SourceRefType sourceRefType,
      Long sourceRefId,
      NotificationSubType subType,
      PushMessage message) {
    try {
      return Optional.ofNullable(
          transactionTemplate()
              .execute(
                  status ->
                      notificationLogRepository.saveAndFlush(
                          NotificationLogEntity.builder()
                              .source(NotificationSource.SYSTEM)
                              .sourceRefType(sourceRefType)
                              .sourceRefId(sourceRefId)
                              .type(NotificationType.AUTO_CHAT)
                              .subType(subType)
                              .title(message.title())
                              .body(message.body())
                              .payload(toJson(message.data().toMap()))
                              .build())));
    } catch (DataIntegrityViolationException e) {
      return Optional.empty();
    }
  }

  /**
   * Sends in FCM-sized batches, clears tokens FCM reports as stale, returns the delivered count.
   */
  private int send(
      List<String> tokens, PushMessage message, Long notificationLogId, String analyticsLabel) {
    // FCM data = business payload + notificationLogId so the device can report opens back
    Map<String, String> fcmData = message.data().withNotificationLogId(notificationLogId).toMap();

    int delivered = 0;
    for (int i = 0; i < tokens.size(); i += FCM_BATCH_SIZE) {
      List<String> batch = tokens.subList(i, Math.min(i + FCM_BATCH_SIZE, tokens.size()));
      MulticastResult result =
          fcmSender.sendMulticast(batch, message.title(), message.body(), fcmData, analyticsLabel);
      clearStaleTokens(result.staleTokens());
      delivered += result.successCount();
    }
    return delivered;
  }

  private void recordCounts(NotificationLogEntity claim, int recipients, int delivered) {
    claim.setRecipientCount(recipients);
    claim.setSuccessCount(delivered);
    notificationLogRepository.save(claim);
  }

  /** The @Modifying query needs a transaction, and the send path runs outside one. */
  private void clearStaleTokens(List<String> staleTokens) {
    transactionTemplate()
        .executeWithoutResult(
            status -> staleTokens.forEach(userAuthSessionRepository::clearNotificationToken));
  }

  private TransactionTemplate transactionTemplate() {
    return new TransactionTemplate(transactionManager);
  }

  private String toJson(Map<String, String> map) {
    try {
      return objectMapper.writeValueAsString(map);
    } catch (JsonProcessingException e) {
      return "{}";
    }
  }
}
