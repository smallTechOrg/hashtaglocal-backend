package org.smalltech.hashtaglocal_backend.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import lombok.Builder;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.smalltech.hashtaglocal_backend.entity.BulletinEntity;
import org.smalltech.hashtaglocal_backend.entity.Locality;
import org.smalltech.hashtaglocal_backend.entity.NotificationLogEntity;
import org.smalltech.hashtaglocal_backend.infra.notification.FcmSender;
import org.smalltech.hashtaglocal_backend.infra.notification.FcmSender.MulticastResult;
import org.smalltech.hashtaglocal_backend.model.NotificationSource;
import org.smalltech.hashtaglocal_backend.model.NotificationType;
import org.smalltech.hashtaglocal_backend.repository.BulletinRepository;
import org.smalltech.hashtaglocal_backend.repository.NotificationLogRepository;
import org.smalltech.hashtaglocal_backend.repository.UserAuthSessionRepository;
import org.smalltech.hashtaglocal_backend.repository.UserRepository;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Daily bulletin push. For every saved-user locality it checks that today's bulletin was generated,
 * retries generation for any locality the 8 AM run missed, and then notifies the users of each
 * locality whose bulletin is ready. Localities that still fail are left without a notification.
 * Idempotent — each bulletin is pushed at most once, tracked in {@code notification_log}
 * (sourceRefType=BULLETIN, sourceRefId=bulletin id), so a re-run only covers what's left.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class BulletinNotificationService {

  static final String SOURCE_REF_TYPE = "BULLETIN";

  // FCM rejects multicast calls with more than 500 tokens.
  private static final int FCM_BATCH_SIZE = 500;
  private static final int MAX_BODY_LENGTH = 180;
  private static final String FALLBACK_BODY = "Today's weather and quiz are ready.";

  private final UserRepository userRepository;
  private final BulletinRepository bulletinRepository;
  private final BulletinGenerationService bulletinGenerationService;
  private final UserAuthSessionRepository userAuthSessionRepository;
  private final NotificationLogRepository notificationLogRepository;
  private final FcmSender fcmSender;
  private final ObjectMapper objectMapper;

  /**
   * Self-reference so {@link #notifyLocality} is invoked through the transactional proxy rather
   * than a plain {@code this} call.
   */
  private final ObjectProvider<BulletinNotificationService> selfProvider;

  public enum Outcome {
    SENT,
    ALREADY_SENT,
    NO_RECIPIENTS
  }

  /** Runs the check → generate missing → notify cycle for today. Returns a per-run summary. */
  public NotificationRunResult runCycle() {
    LocalDate today = LocalDate.now(ZoneId.of("Asia/Kolkata"));
    List<Locality> localities = userRepository.findDistinctUserLocalities();
    log.info(
        "Bulletin notification run started for {} user localities (date={})",
        localities.size(),
        today);

    BulletinNotificationService self = selfProvider.getObject();
    int regenerated = 0;
    int generationFailed = 0;
    int notified = 0;
    int alreadyNotified = 0;
    int noRecipients = 0;
    int notifyFailed = 0;

    for (Locality locality : localities) {
      Optional<BulletinEntity> bulletin = findReadyBulletin(locality, today);
      if (bulletin.isEmpty()) {
        try {
          bulletinGenerationService.generateForLocality(locality, today);
        } catch (Exception e) {
          log.error(
              "Bulletin retry failed for locality {} ({}): {}",
              locality.getId(),
              locality.getHashtag(),
              e.getMessage());
        }
        bulletin = findReadyBulletin(locality, today);
        if (bulletin.isEmpty()) {
          generationFailed++;
          continue;
        }
        regenerated++;
      }

      try {
        switch (self.notifyLocality(locality, bulletin.get().getId())) {
          case SENT -> notified++;
          case ALREADY_SENT -> alreadyNotified++;
          case NO_RECIPIENTS -> noRecipients++;
        }
      } catch (Exception e) {
        notifyFailed++;
        log.error(
            "Bulletin notification failed for locality {} ({}): {}",
            locality.getId(),
            locality.getHashtag(),
            e.getMessage());
      }
    }

    log.info(
        "Bulletin notification run finished: {} notified, {} already notified, {} no recipients,"
            + " {} notify failed, {} regenerated, {} generation failed",
        notified,
        alreadyNotified,
        noRecipients,
        notifyFailed,
        regenerated,
        generationFailed);
    return NotificationRunResult.builder()
        .date(today)
        .totalLocalities(localities.size())
        .regenerated(regenerated)
        .generationFailed(generationFailed)
        .notified(notified)
        .alreadyNotified(alreadyNotified)
        .noRecipients(noRecipients)
        .notifyFailed(notifyFailed)
        .build();
  }

  /**
   * Pushes the bulletin to every active device of users whose home location is in {@code locality}.
   * Skips if this bulletin was already pushed. Runs in its own transaction.
   */
  @Transactional
  public Outcome notifyLocality(Locality locality, Long bulletinId) {
    if (notificationLogRepository.existsBySourceRefTypeAndSourceRefId(
        SOURCE_REF_TYPE, bulletinId)) {
      return Outcome.ALREADY_SENT;
    }

    List<String> tokens =
        userAuthSessionRepository.findActiveNotificationTokensByLocalityId(locality.getId());
    if (tokens.isEmpty()) {
      return Outcome.NO_RECIPIENTS;
    }

    BulletinEntity bulletin =
        bulletinRepository
            .findById(bulletinId)
            .orElseThrow(() -> new IllegalStateException("Bulletin not found: " + bulletinId));
    String title = "Daily Bulletin — " + locality.getName();
    String body = bodyFrom(bulletin);

    Map<String, String> payload =
        Map.of(
            "type", NotificationType.BULLETIN.name(),
            "bulletinId", bulletinId.toString(),
            "localityId", locality.getId().toString(),
            "date", bulletin.getDate().toString());

    NotificationLogEntity logEntry =
        notificationLogRepository.save(
            NotificationLogEntity.builder()
                .source(NotificationSource.SYSTEM)
                .sourceRefType(SOURCE_REF_TYPE)
                .sourceRefId(bulletinId)
                .type(NotificationType.BULLETIN)
                .title(title)
                .body(body)
                .payload(toJson(payload))
                .build());

    // FCM data = business payload + notificationLogId so the device can report opens back
    Map<String, String> fcmData = new HashMap<>(payload);
    fcmData.put("notificationLogId", logEntry.getId().toString());

    int totalSuccess = 0;
    for (int i = 0; i < tokens.size(); i += FCM_BATCH_SIZE) {
      List<String> batch = tokens.subList(i, Math.min(i + FCM_BATCH_SIZE, tokens.size()));
      MulticastResult result =
          fcmSender.sendMulticast(batch, title, body, fcmData, "bulletin_system");
      result.staleTokens().forEach(userAuthSessionRepository::clearNotificationToken);
      totalSuccess += result.successCount();
    }

    log.info(
        "BULLETIN sent for locality {} ({}): {} recipients, {} FCM accepted",
        locality.getId(),
        locality.getHashtag(),
        tokens.size(),
        totalSuccess);

    logEntry.setRecipientCount(tokens.size());
    logEntry.setSuccessCount(totalSuccess);
    notificationLogRepository.save(logEntry);
    return Outcome.SENT;
  }

  /** Today's bulletin for the locality, only once the weather run has filled it in. */
  private Optional<BulletinEntity> findReadyBulletin(Locality locality, LocalDate date) {
    return bulletinRepository
        .findByLocalityIdAndDate(locality.getId(), date)
        .filter(b -> b.getPeriodicData() != null);
  }

  private String bodyFrom(BulletinEntity bulletin) {
    Object text = bulletin.getSummary() != null ? bulletin.getSummary().get("text") : null;
    if (text == null || text.toString().isBlank()) {
      return FALLBACK_BODY;
    }
    String body = text.toString().strip();
    return body.length() <= MAX_BODY_LENGTH
        ? body
        : body.substring(0, MAX_BODY_LENGTH - 1).stripTrailing() + "…";
  }

  private String toJson(Map<String, String> map) {
    try {
      return objectMapper.writeValueAsString(map);
    } catch (JsonProcessingException e) {
      return "{}";
    }
  }

  @Data
  @Builder
  public static class NotificationRunResult {
    private LocalDate date;
    private int totalLocalities;
    private int regenerated;
    private int generationFailed;
    private int notified;
    private int alreadyNotified;
    private int noRecipients;
    private int notifyFailed;
  }
}
