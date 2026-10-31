package org.smalltech.hashtaglocal_backend.service;

import static org.smalltech.hashtaglocal_backend.config.NotificationConstants.FCM_BATCH_SIZE;
import static org.smalltech.hashtaglocal_backend.config.NotificationConstants.MAX_BODY_LENGTH;
import static org.smalltech.hashtaglocal_backend.config.NotificationConstants.MAX_TITLE_LENGTH;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.smalltech.hashtaglocal_backend.entity.BulletinEntity;
import org.smalltech.hashtaglocal_backend.entity.NotificationLogEntity;
import org.smalltech.hashtaglocal_backend.event.FeedPublishedEvent;
import org.smalltech.hashtaglocal_backend.infra.notification.FcmSender;
import org.smalltech.hashtaglocal_backend.infra.notification.FcmSender.MulticastResult;
import org.smalltech.hashtaglocal_backend.model.NotificationSource;
import org.smalltech.hashtaglocal_backend.model.NotificationSubType;
import org.smalltech.hashtaglocal_backend.model.NotificationType;
import org.smalltech.hashtaglocal_backend.model.SourceRefType;
import org.smalltech.hashtaglocal_backend.repository.BulletinRepository;
import org.smalltech.hashtaglocal_backend.repository.NotificationLogRepository;
import org.smalltech.hashtaglocal_backend.repository.UserAuthSessionRepository;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Sends a push to a locality's users when a post is published to its feed ({@link
 * FeedPublishedEvent}). Today only BULLETIN posts are handled, as the "Daily Bulletin" push; other
 * feed post kinds can be added here.
 *
 * <p>At-most-once: the {@code notification_log} claim row is committed (and protected by a unique
 * index on the bulletin id) BEFORE any FCM call, so a re-run or a concurrent run can never send
 * twice — at the cost of not retrying a send that failed part-way.
 */
@Component
@ConditionalOnProperty(name = "bulletin.notification.enabled", havingValue = "true")
@RequiredArgsConstructor
@Slf4j
public class FeedNotificationListener {

  private static final ZoneId IST = ZoneId.of("Asia/Kolkata");

  static final String TITLE_PREFIX = "Daily Bulletin — ";

  /**
   * Used only when the bulletin exists but has no summary text (e.g. an admin-created row). A
   * missing bulletin gets no push at all — see {@link #notifyLocality}.
   */
  static final String FALLBACK_SUMMARY = "Today's weather update is ready.";

  /** FCM {@code type} the mobile app routes on (the log's own type is AUTO_CHAT). */
  private static final String PAYLOAD_TYPE = "BULLETIN";

  private static final String ANALYTICS_LABEL = "bulletin_system";
  private static final String KEY_TYPE = "type";
  private static final String KEY_NOTIFICATION_LOG_ID = "notificationLogId";
  private static final String KEY_BULLETIN_ID = "bulletinId";
  private static final String KEY_LOCALITY_ID = "localityId";
  private static final String KEY_DATE = "date";

  private final BulletinRepository bulletinRepository;
  private final UserAuthSessionRepository userAuthSessionRepository;
  private final NotificationLogRepository notificationLogRepository;
  private final FcmSender fcmSender;
  private final ObjectMapper objectMapper;
  private final PlatformTransactionManager transactionManager;

  // fallbackExecution: the generation cron may publish outside a transaction.
  @Async
  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
  public void onFeedPublished(FeedPublishedEvent event) {
    try {
      notifyLocality(event.bulletin().getId());
    } catch (Exception e) {
      log.error(
          "Bulletin notification failed for bulletin {}: {}",
          event.bulletin().getId(),
          e.getMessage(),
          e);
    }
  }

  /** What is pushed: the visible title and body plus the data the app routes on. */
  private record PushMessage(String title, String body, Map<String, String> payload) {}

  void notifyLocality(Long bulletinId) {
    if (alreadyNotified(bulletinId)) {
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
    if (!bulletin.getDate().equals(LocalDate.now(IST))) {
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

    NotificationLogEntity claim = claim(bulletinId, message).orElse(null);
    if (claim == null) {
      log.info("Bulletin {} already claimed by another run — nothing sent", bulletinId);
      return;
    }

    int delivered = send(tokens, message, claim.getId());

    claim.setRecipientCount(tokens.size());
    claim.setSuccessCount(delivered);
    notificationLogRepository.save(claim);
    log.info(
        "Bulletin {} notified locality {}: {} recipients, {} delivered",
        bulletinId,
        localityId,
        tokens.size(),
        delivered);
  }

  /** The duplicate-send guard, in the shape of the documented access pattern. */
  private boolean alreadyNotified(Long bulletinId) {
    return notificationLogRepository.existsByTypeAndSubTypeAndSourceRefTypeAndSourceRefId(
        NotificationType.AUTO_CHAT,
        NotificationSubType.CHAT_BULLETIN,
        SourceRefType.BULLETIN,
        bulletinId);
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

  /**
   * Inserts the log row in its own committed transaction BEFORE anything is sent; the unique index
   * rejects a parallel duplicate here. Empty means another run already claimed this bulletin.
   */
  private Optional<NotificationLogEntity> claim(Long bulletinId, PushMessage message) {
    try {
      return Optional.ofNullable(
          transactionTemplate()
              .execute(
                  status ->
                      notificationLogRepository.saveAndFlush(
                          NotificationLogEntity.builder()
                              .source(NotificationSource.SYSTEM)
                              .sourceRefType(SourceRefType.BULLETIN)
                              .sourceRefId(bulletinId)
                              .type(NotificationType.AUTO_CHAT)
                              .subType(NotificationSubType.CHAT_BULLETIN)
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
  private int send(List<String> tokens, PushMessage message, Long notificationLogId) {
    // FCM data = business payload + notificationLogId so the device can report opens back
    Map<String, String> fcmData = new HashMap<>(message.payload());
    fcmData.put(KEY_NOTIFICATION_LOG_ID, notificationLogId.toString());

    int delivered = 0;
    for (int i = 0; i < tokens.size(); i += FCM_BATCH_SIZE) {
      List<String> batch = tokens.subList(i, Math.min(i + FCM_BATCH_SIZE, tokens.size()));
      MulticastResult result =
          fcmSender.sendMulticast(batch, message.title(), message.body(), fcmData, ANALYTICS_LABEL);
      clearStaleTokens(result.staleTokens());
      delivered += result.successCount();
    }
    return delivered;
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
    String title = TITLE_PREFIX + localityName;
    if (title.length() <= MAX_TITLE_LENGTH) {
      return title;
    }
    return title.substring(0, MAX_TITLE_LENGTH - 1) + "…";
  }

  /** First sentence of the summary, capped at {@code MAX_BODY_LENGTH} on a word boundary. */
  static String buildBody(String text) {
    if (text == null || text.isBlank()) {
      return FALLBACK_SUMMARY;
    }
    String trimmed = text.strip();
    int end = firstSentenceEnd(trimmed);
    String sentence = end < 0 ? trimmed : trimmed.substring(0, end + 1);
    if (sentence.length() <= MAX_BODY_LENGTH) {
      return sentence;
    }
    int cut = sentence.lastIndexOf(' ', MAX_BODY_LENGTH);
    String head = sentence.substring(0, cut > 0 ? cut : MAX_BODY_LENGTH);
    int last = head.length();
    while (last > 0 && " ,;:-".indexOf(head.charAt(last - 1)) >= 0) {
      last--;
    }
    return head.substring(0, last) + "…";
  }

  /** Index of the terminator ('.', '!' or '?' followed by a space) ending the first sentence. */
  private static int firstSentenceEnd(String s) {
    for (int i = 0; i < s.length() - 1; i++) {
      char c = s.charAt(i);
      if ((c == '.' || c == '!' || c == '?') && s.charAt(i + 1) == ' ') {
        return i;
      }
    }
    return -1;
  }

  private String toJson(Map<String, String> map) {
    try {
      return objectMapper.writeValueAsString(map);
    } catch (JsonProcessingException e) {
      return "{}";
    }
  }
}
