package org.smalltech.hashtaglocal_backend.service;

import static org.smalltech.hashtaglocal_backend.config.NotificationConstants.FCM_BATCH_SIZE;
import static org.smalltech.hashtaglocal_backend.config.NotificationConstants.MAX_BODY_LENGTH;
import static org.smalltech.hashtaglocal_backend.config.NotificationConstants.MAX_TITLE_LENGTH;
import static org.smalltech.hashtaglocal_backend.config.TimeConstants.APP_ZONE;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.smalltech.hashtaglocal_backend.entity.BulletinEntity;
import org.smalltech.hashtaglocal_backend.entity.EventEntity;
import org.smalltech.hashtaglocal_backend.entity.FeedPostContentEntity;
import org.smalltech.hashtaglocal_backend.entity.FeedPostEntity;
import org.smalltech.hashtaglocal_backend.entity.IssueEntity;
import org.smalltech.hashtaglocal_backend.entity.Locality;
import org.smalltech.hashtaglocal_backend.entity.NotificationLogEntity;
import org.smalltech.hashtaglocal_backend.entity.UserEntity;
import org.smalltech.hashtaglocal_backend.event.FeedPublishedEvent;
import org.smalltech.hashtaglocal_backend.infra.notification.FcmSender;
import org.smalltech.hashtaglocal_backend.infra.notification.FcmSender.MulticastResult;
import org.smalltech.hashtaglocal_backend.model.FeedPostKind;
import org.smalltech.hashtaglocal_backend.model.FeedPostStatus;
import org.smalltech.hashtaglocal_backend.model.IssueStatusModel;
import org.smalltech.hashtaglocal_backend.model.MediaTypeModel;
import org.smalltech.hashtaglocal_backend.model.NotificationSource;
import org.smalltech.hashtaglocal_backend.model.NotificationSubType;
import org.smalltech.hashtaglocal_backend.model.NotificationType;
import org.smalltech.hashtaglocal_backend.model.SourceRefType;
import org.smalltech.hashtaglocal_backend.model.UserRole;
import org.smalltech.hashtaglocal_backend.repository.BulletinRepository;
import org.smalltech.hashtaglocal_backend.repository.FeedPostRepository;
import org.smalltech.hashtaglocal_backend.repository.NotificationLogRepository;
import org.smalltech.hashtaglocal_backend.repository.UserAuthSessionRepository;
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
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class FeedNotificationListener {

  static final String TITLE_PREFIX = "Daily Bulletin — ";

  /**
   * Used only when the bulletin exists but has no summary text (e.g. an admin-created row). A
   * missing bulletin gets no push at all — see {@link #notifyLocality}.
   */
  static final String FALLBACK_SUMMARY = "Today's weather update is ready.";

  /** Chat push body when a post has nothing better to show. */
  static final String FALLBACK_CHAT_BODY = "New post in the chat";

  /** Longest chat push body; longer text is cut at a word boundary with an ellipsis. */
  static final int CHAT_MAX_BODY_LENGTH = 230;

  /** FCM {@code type} the mobile app routes on (the log's own type is AUTO_CHAT). */
  private static final String PAYLOAD_TYPE = "BULLETIN";

  /** FCM {@code type} for every non-bulletin, non-issue chat post (app opens the chat). */
  private static final String CHAT_PAYLOAD_TYPE = "CHAT";

  /** FCM {@code type} for an issue update (app opens the issue detail page for {@code issueId}). */
  private static final String ISSUE_PAYLOAD_TYPE = "ISSUE_UPDATE";

  /** Issue push body when the issue has no description. */
  static final String FALLBACK_ISSUE_BODY = "Tap to see the details";

  private static final String ANALYTICS_LABEL = "bulletin_system";
  private static final String CHAT_ANALYTICS_LABEL = "chat_system";
  private static final String ISSUE_ANALYTICS_LABEL = "issue_update_system";
  private static final String KEY_TYPE = "type";
  private static final String KEY_NOTIFICATION_LOG_ID = "notificationLogId";
  private static final String KEY_BULLETIN_ID = "bulletinId";
  private static final String KEY_LOCALITY_ID = "localityId";
  private static final String KEY_DATE = "date";
  private static final String KEY_FEED_POST_ID = "feedPostId";
  private static final String KEY_KIND = "kind";
  private static final String KEY_HASHTAG = "hashtag";
  private static final String KEY_ISSUE_ID = "issueId";
  private static final String KEY_EVENT_ID = "eventId";
  private static final String KEY_STATUS = "status";

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

  /** What is pushed: the visible title and body plus the data the app routes on. */
  private record PushMessage(String title, String body, Map<String, String> payload) {}

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

    PushMessage message = buildMessage(bulletin);

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

  private PushMessage buildMessage(BulletinEntity bulletin) {
    Object summaryText = bulletin.getSummary() != null ? bulletin.getSummary().get("text") : null;

    Map<String, String> payload = new HashMap<>();
    payload.put(KEY_TYPE, PAYLOAD_TYPE);
    payload.put(KEY_BULLETIN_ID, bulletin.getId().toString());
    payload.put(KEY_LOCALITY_ID, bulletin.getLocality().getId().toString());
    payload.put(KEY_DATE, bulletin.getDate().toString());

    return new PushMessage(
        buildTitle(bulletin.getLocality().getName()),
        buildBody(summaryText != null ? summaryText.toString() : null),
        payload);
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
        buildChatMessage(post),
        CHAT_ANALYTICS_LABEL);
  }

  private PushMessage buildChatMessage(FeedPostEntity post) {
    Locality locality = post.getLocality();
    FeedPostContentEntity content = post.getContent();

    Map<String, String> payload = new HashMap<>();
    payload.put(KEY_TYPE, CHAT_PAYLOAD_TYPE);
    payload.put(KEY_FEED_POST_ID, post.getId().toString());
    payload.put(KEY_KIND, post.getKind().name());
    payload.put(KEY_LOCALITY_ID, locality.getId().toString());
    payload.put(KEY_HASHTAG, locality.getHashtag());
    if (content != null && content.getEvent() != null) {
      payload.put(KEY_EVENT_ID, content.getEvent().getId().toString());
    }

    return new PushMessage(
        buildChatTitle(locality),
        shorten(buildChatText(post), FALLBACK_CHAT_BODY, CHAT_MAX_BODY_LENGTH),
        payload);
  }

  /**
   * The locality's name, e.g. "Koramangala" (its hashtag if it has no name), cut with an ellipsis
   * if it would exceed the title limit.
   */
  static String buildChatTitle(Locality locality) {
    return shortenTitle(localityName(locality));
  }

  /**
   * The full push text for a chat post, e.g. "alice: hello" or "ravi shared a link: {title}". Admin
   * posts show just the text, with no name. Returns null when there is nothing to show.
   */
  static String buildChatText(FeedPostEntity post) {
    FeedPostContentEntity c = post.getContent();
    String author = displayName(post.getAuthor());
    String text = c != null ? firstNonBlank(c.getText()) : null;

    return switch (post.getKind()) {
      case LINK -> {
        String title = c != null ? firstNonBlank(c.getTitle()) : null;
        if (author == null) {
          yield firstNonBlank(title, text);
        }
        yield title != null ? author + " shared a link: " + title : author + " shared a link";
      }
      case EVENT_REF -> {
        EventEntity event = c != null ? c.getEvent() : null;
        String name = event != null ? firstNonBlank(event.getName()) : null;
        yield withAuthor(author, firstNonBlank(text, name != null ? "New event: " + name : null));
      }
      case MEDIA -> {
        boolean video =
            c != null && c.getMedia() != null && c.getMedia().getType() == MediaTypeModel.VIDEO;
        yield withAuthor(author, firstNonBlank(text, video ? "Shared a video" : "Shared a photo"));
      }
      default -> withAuthor(author, firstNonBlank(text, c != null ? c.getTitle() : null));
    };
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
        buildIssueMessage(post, issue, resolved),
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

  private PushMessage buildIssueMessage(FeedPostEntity post, IssueEntity issue, boolean resolved) {
    Locality locality = post.getLocality();

    Map<String, String> payload = new HashMap<>();
    payload.put(KEY_TYPE, ISSUE_PAYLOAD_TYPE);
    payload.put(KEY_ISSUE_ID, issue.getId().toString());
    payload.put(
        KEY_STATUS, resolved ? IssueStatusModel.RESOLVED.name() : IssueStatusModel.OPEN.name());
    payload.put(KEY_FEED_POST_ID, post.getId().toString());
    payload.put(KEY_LOCALITY_ID, locality.getId().toString());
    payload.put(KEY_HASHTAG, locality.getHashtag());

    return new PushMessage(
        buildIssueTitle(locality, resolved),
        shorten(issue.getDescription(), FALLBACK_ISSUE_BODY, CHAT_MAX_BODY_LENGTH),
        payload);
  }

  /** "New issue in {locality}" or "Issue resolved in {locality}". */
  static String buildIssueTitle(Locality locality, boolean resolved) {
    return shortenTitle(
        (resolved ? "Issue resolved in " : "New issue in ") + localityName(locality));
  }

  // ---------------------------------------------------------------------------------------------
  // Helpers shared by the chat and issue pushes
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

  private static String localityName(Locality locality) {
    String name = firstNonBlank(locality.getName(), locality.getHashtag());
    return name != null ? name : "";
  }

  /** The name to show for a post's author: none for admins (their posts read as official). */
  private static String displayName(UserEntity user) {
    if (user == null || user.getRole() == UserRole.ADMIN) {
      return null;
    }
    return firstNonBlank(user.getUsername());
  }

  /** {@code "{author}: {text}"}; the text alone when there is no author name. */
  private static String withAuthor(String author, String text) {
    if (author == null || text == null) {
      return text;
    }
    return author + ": " + text;
  }

  private static String firstNonBlank(String... values) {
    for (String v : values) {
      if (v != null && !v.isBlank()) {
        return v.strip();
      }
    }
    return null;
  }

  // ---------------------------------------------------------------------------------------------
  // Shared: dedupe, claim, send
  // ---------------------------------------------------------------------------------------------

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
                              .payload(toJson(message.payload()))
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
    Map<String, String> fcmData = new HashMap<>(message.payload());
    fcmData.put(KEY_NOTIFICATION_LOG_ID, notificationLogId.toString());

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

  /** "Daily Bulletin — {locality}", cut with an ellipsis if it would exceed the title limit. */
  static String buildTitle(String localityName) {
    return shortenTitle(TITLE_PREFIX + localityName);
  }

  private static String shortenTitle(String title) {
    if (title.length() <= MAX_TITLE_LENGTH) {
      return title;
    }
    return title.substring(0, MAX_TITLE_LENGTH - 1) + "…";
  }

  /**
   * The whole summary, truncated at a word boundary with an ellipsis when it exceeds {@code
   * MAX_BODY_LENGTH}. Deliberately not cut at the first sentence: sentences can be far too long or
   * far too short to make a useful push body on their own.
   */
  static String buildBody(String text) {
    return shorten(text, FALLBACK_SUMMARY, MAX_BODY_LENGTH);
  }

  /** {@code text} cut to {@code maxLength} on a word boundary; {@code fallback} if blank. */
  static String shorten(String text, String fallback, int maxLength) {
    if (text == null || text.isBlank()) {
      return fallback;
    }
    String trimmed = text.strip();
    if (trimmed.length() <= maxLength) {
      return trimmed;
    }
    int cut = trimmed.lastIndexOf(' ', maxLength - 1);
    String head = trimmed.substring(0, cut > 0 ? cut : maxLength - 1);
    int last = head.length();
    while (last > 0 && " ,;:-".indexOf(head.charAt(last - 1)) >= 0) {
      last--;
    }
    return head.substring(0, last) + "…";
  }

  private String toJson(Map<String, String> map) {
    try {
      return objectMapper.writeValueAsString(map);
    } catch (JsonProcessingException e) {
      return "{}";
    }
  }
}
