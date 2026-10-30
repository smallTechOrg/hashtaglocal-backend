package org.smalltech.hashtaglocal_backend.service;

import static org.smalltech.hashtaglocal_backend.config.NotificationConstants.FCM_BATCH_SIZE;
import static org.smalltech.hashtaglocal_backend.config.NotificationConstants.KEY_NOTIFICATION_LOG_ID;
import static org.smalltech.hashtaglocal_backend.config.NotificationConstants.KEY_TYPE;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.smalltech.hashtaglocal_backend.entity.BulletinEntity;
import org.smalltech.hashtaglocal_backend.entity.NotificationLogEntity;
import org.smalltech.hashtaglocal_backend.event.BulletinPublishedEvent;
import org.smalltech.hashtaglocal_backend.infra.notification.FcmSender;
import org.smalltech.hashtaglocal_backend.infra.notification.FcmSender.MulticastResult;
import org.smalltech.hashtaglocal_backend.model.NotificationRefType;
import org.smalltech.hashtaglocal_backend.model.NotificationSource;
import org.smalltech.hashtaglocal_backend.model.NotificationSubType;
import org.smalltech.hashtaglocal_backend.model.NotificationType;
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
 * Sends the "Daily Bulletin" push to a locality's users as soon as its BULLETIN feed post is
 * published. At-most-once: the {@code notification_log} claim row is committed (and protected by a
 * unique index on the bulletin id) BEFORE any FCM call, so a re-run or a concurrent run can never
 * send twice — at the cost of not retrying a send that failed part-way.
 */
@Component
@ConditionalOnProperty(name = "bulletin.notification.enabled", havingValue = "true")
@RequiredArgsConstructor
@Slf4j
public class BulletinNotificationListener {

  static final String TITLE_PREFIX = "Daily Bulletin — ";
  static final String FALLBACK_BODY = "Today's weather update and quiz are ready.";
  static final int MAX_BODY_LENGTH = 180;

  /** FCM {@code type} the mobile app routes on (the log's own type is AUTO_CHAT). */
  private static final String PAYLOAD_TYPE = "BULLETIN";

  private static final String ANALYTICS_LABEL = "bulletin_system";
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
  public void onBulletinPublished(BulletinPublishedEvent event) {
    try {
      notifyLocality(event.bulletinId(), event.localityId());
    } catch (Exception e) {
      log.error(
          "Bulletin notification failed for bulletin {} (locality {}): {}",
          event.bulletinId(),
          event.localityId(),
          e.getMessage(),
          e);
    }
  }

  void notifyLocality(Long bulletinId, Long localityId) {
    if (notificationLogRepository.existsByTypeAndSubTypeAndSourceRefTypeAndSourceRefId(
        NotificationType.AUTO_CHAT,
        NotificationSubType.CHAT_BULLETIN,
        NotificationRefType.BULLETIN,
        bulletinId)) {
      log.debug("Bulletin {} already notified — skipping", bulletinId);
      return;
    }

    BulletinEntity bulletin = bulletinRepository.findByIdWithLocality(bulletinId).orElse(null);
    if (bulletin == null) {
      log.warn("Bulletin {} not found — skipping notification", bulletinId);
      return;
    }

    List<String> tokens =
        userAuthSessionRepository.findActiveNotificationTokensByLocalityId(localityId);
    if (tokens.isEmpty()) {
      return;
    }

    String title = TITLE_PREFIX + bulletin.getLocality().getName();
    Object summaryText = bulletin.getSummary() != null ? bulletin.getSummary().get("text") : null;
    String body = buildBody(summaryText != null ? summaryText.toString() : null);

    Map<String, String> payload = new HashMap<>();
    payload.put(KEY_TYPE, PAYLOAD_TYPE);
    payload.put(KEY_BULLETIN_ID, bulletinId.toString());
    payload.put(KEY_LOCALITY_ID, localityId.toString());
    payload.put(KEY_DATE, bulletin.getDate().toString());

    TransactionTemplate tx = new TransactionTemplate(transactionManager);

    // CLAIM: committed before sending; the unique index rejects a parallel duplicate here.
    NotificationLogEntity claim;
    try {
      claim =
          tx.execute(
              status ->
                  notificationLogRepository.saveAndFlush(
                      NotificationLogEntity.builder()
                          .source(NotificationSource.SYSTEM)
                          .sourceRefType(NotificationRefType.BULLETIN)
                          .sourceRefId(bulletinId)
                          .type(NotificationType.AUTO_CHAT)
                          .subType(NotificationSubType.CHAT_BULLETIN)
                          .title(title)
                          .body(body)
                          .payload(toJson(payload))
                          .build()));
    } catch (DataIntegrityViolationException e) {
      log.info("Bulletin {} already claimed by another run — nothing sent", bulletinId);
      return;
    }

    // FCM data = business payload + notificationLogId so the device can report opens back
    Map<String, String> fcmData = new HashMap<>(payload);
    fcmData.put(KEY_NOTIFICATION_LOG_ID, claim.getId().toString());

    int successTotal = 0;
    for (int i = 0; i < tokens.size(); i += FCM_BATCH_SIZE) {
      List<String> batch = tokens.subList(i, Math.min(i + FCM_BATCH_SIZE, tokens.size()));
      MulticastResult result =
          fcmSender.sendMulticast(batch, title, body, fcmData, ANALYTICS_LABEL);
      // @Modifying query needs a transaction; this method runs outside one.
      tx.executeWithoutResult(
          status ->
              result.staleTokens().forEach(userAuthSessionRepository::clearNotificationToken));
      successTotal += result.successCount();
    }

    claim.setRecipientCount(tokens.size());
    claim.setSuccessCount(successTotal);
    notificationLogRepository.save(claim);
    log.info(
        "Bulletin {} notified locality {}: {} recipients, {} delivered",
        bulletinId,
        localityId,
        tokens.size(),
        successTotal);
  }

  /** First sentence of the summary, capped at {@code MAX_BODY_LENGTH} on a word boundary. */
  static String buildBody(String text) {
    if (text == null || text.isBlank()) {
      return FALLBACK_BODY;
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
