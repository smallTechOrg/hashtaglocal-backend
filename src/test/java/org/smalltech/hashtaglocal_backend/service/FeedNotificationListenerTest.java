package org.smalltech.hashtaglocal_backend.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.smalltech.hashtaglocal_backend.config.NotificationConstants;
import org.smalltech.hashtaglocal_backend.entity.BulletinEntity;
import org.smalltech.hashtaglocal_backend.entity.Locality;
import org.smalltech.hashtaglocal_backend.entity.NotificationLogEntity;
import org.smalltech.hashtaglocal_backend.infra.notification.FcmSender;
import org.smalltech.hashtaglocal_backend.infra.notification.FcmSender.MulticastResult;
import org.smalltech.hashtaglocal_backend.model.NotificationSubType;
import org.smalltech.hashtaglocal_backend.model.NotificationType;
import org.smalltech.hashtaglocal_backend.model.SourceRefType;
import org.smalltech.hashtaglocal_backend.repository.BulletinRepository;
import org.smalltech.hashtaglocal_backend.repository.NotificationLogRepository;
import org.smalltech.hashtaglocal_backend.repository.UserAuthSessionRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.SimpleTransactionStatus;

class FeedNotificationListenerTest {

  private BulletinRepository bulletinRepository;
  private UserAuthSessionRepository sessionRepository;
  private NotificationLogRepository logRepository;
  private FcmSender fcmSender;
  private FeedNotificationListener listener;

  @BeforeEach
  void setUp() {
    bulletinRepository = mock(BulletinRepository.class);
    sessionRepository = mock(UserAuthSessionRepository.class);
    logRepository = mock(NotificationLogRepository.class);
    fcmSender = mock(FcmSender.class);
    PlatformTransactionManager txManager = mock(PlatformTransactionManager.class);
    when(txManager.getTransaction(any())).thenReturn(new SimpleTransactionStatus());
    listener =
        new FeedNotificationListener(
            bulletinRepository,
            sessionRepository,
            logRepository,
            fcmSender,
            new ObjectMapper(),
            txManager);
  }

  private BulletinEntity bulletin() {
    Locality locality = new Locality();
    locality.setId(1L);
    locality.setName("Koramangala");
    return BulletinEntity.builder()
        .id(7L)
        .locality(locality)
        .date(LocalDate.now(ZoneId.of("Asia/Kolkata")))
        .summary(Map.of("text", "Light showers expected after 5 PM. Carry an umbrella."))
        .build();
  }

  // ---- buildBody ----

  @Test
  void buildBody_blankFallsBack() {
    assertEquals(
        FeedNotificationListener.FALLBACK_SUMMARY, FeedNotificationListener.buildBody(null));
    assertEquals(
        FeedNotificationListener.FALLBACK_SUMMARY, FeedNotificationListener.buildBody("  "));
  }

  @Test
  void buildBody_takesFirstSentence() {
    assertEquals(
        "Light showers expected after 5 PM.",
        FeedNotificationListener.buildBody(
            "Light showers expected after 5 PM. Carry an umbrella."));
  }

  @Test
  void buildBody_noSentenceBreakUsesWholeText() {
    assertEquals("Sunny all day", FeedNotificationListener.buildBody("Sunny all day"));
  }

  @Test
  void buildBody_doesNotSplitOnDecimal() {
    assertEquals(
        "Highs of 28.5°C today.",
        FeedNotificationListener.buildBody("Highs of 28.5°C today. Stay hydrated."));
  }

  @Test
  void buildBody_longSentenceCutsOnWordWithEllipsis() {
    String body = FeedNotificationListener.buildBody("word, ".repeat(40));
    assertTrue(body.endsWith("…"));
    assertTrue(body.length() <= NotificationConstants.MAX_BODY_LENGTH + 1);
    assertTrue(body.startsWith("word, word"));
    assertEquals("word", body.substring(body.length() - 5, body.length() - 1));
  }

  // ---- buildTitle ----

  @Test
  void buildTitle_prefixesLocalityName() {
    assertEquals(
        "Daily Bulletin — Koramangala", FeedNotificationListener.buildTitle("Koramangala"));
  }

  @Test
  void buildTitle_cutsOverlongNameToTitleLimit() {
    String title = FeedNotificationListener.buildTitle("x".repeat(1000));
    assertEquals(NotificationConstants.MAX_TITLE_LENGTH, title.length());
    assertTrue(title.endsWith("…"));
  }

  // ---- notifyLocality ----

  @Test
  void skipsWhenAlreadyLogged() {
    when(logRepository.existsByTypeAndSubTypeAndSourceRefTypeAndSourceRefId(
            NotificationType.AUTO_CHAT,
            NotificationSubType.CHAT_BULLETIN,
            SourceRefType.BULLETIN,
            7L))
        .thenReturn(true);

    listener.notifyLocality(7L);

    verify(fcmSender, never())
        .sendMulticast(anyList(), anyString(), anyString(), anyMap(), anyString());
    verify(logRepository, never()).saveAndFlush(any());
  }

  @Test
  void missingBulletinSendsNothingAndWritesNoLogRow() {
    when(bulletinRepository.findByIdWithLocality(7L)).thenReturn(Optional.empty());

    listener.notifyLocality(7L);

    verify(sessionRepository, never()).findActiveNotificationTokensByLocalityId(anyLong());
    verify(logRepository, never()).saveAndFlush(any());
    verify(fcmSender, never())
        .sendMulticast(anyList(), anyString(), anyString(), anyMap(), anyString());
  }

  @Test
  void oldBulletinSendsNothingAndWritesNoLogRow() {
    BulletinEntity old = bulletin();
    old.setDate(LocalDate.now(ZoneId.of("Asia/Kolkata")).minusDays(1));
    when(bulletinRepository.findByIdWithLocality(7L)).thenReturn(Optional.of(old));

    listener.notifyLocality(7L);

    verify(sessionRepository, never()).findActiveNotificationTokensByLocalityId(anyLong());
    verify(logRepository, never()).saveAndFlush(any());
    verify(fcmSender, never())
        .sendMulticast(anyList(), anyString(), anyString(), anyMap(), anyString());
  }

  @Test
  void bulletinWithoutSummaryStillSendsWithFallbackSummary() {
    BulletinEntity noSummary = bulletin();
    noSummary.setSummary(null);
    when(bulletinRepository.findByIdWithLocality(7L)).thenReturn(Optional.of(noSummary));
    when(sessionRepository.findActiveNotificationTokensByLocalityId(1L)).thenReturn(List.of("t1"));
    when(logRepository.saveAndFlush(any()))
        .thenReturn(NotificationLogEntity.builder().id(99L).build());
    when(fcmSender.sendMulticast(anyList(), anyString(), anyString(), anyMap(), anyString()))
        .thenReturn(new MulticastResult(Map.of(), List.of()));

    listener.notifyLocality(7L);

    verify(fcmSender)
        .sendMulticast(
            anyList(),
            eq("Daily Bulletin — Koramangala"),
            eq(FeedNotificationListener.FALLBACK_SUMMARY),
            anyMap(),
            anyString());
  }

  @Test
  void skipsQuietlyWhenNoTokens() {
    when(bulletinRepository.findByIdWithLocality(7L)).thenReturn(Optional.of(bulletin()));
    when(sessionRepository.findActiveNotificationTokensByLocalityId(1L)).thenReturn(List.of());

    listener.notifyLocality(7L);

    verify(logRepository, never()).saveAndFlush(any());
    verify(fcmSender, never())
        .sendMulticast(anyList(), anyString(), anyString(), anyMap(), anyString());
  }

  @Test
  void duplicateClaimSendsNothing() {
    when(bulletinRepository.findByIdWithLocality(7L)).thenReturn(Optional.of(bulletin()));
    when(sessionRepository.findActiveNotificationTokensByLocalityId(1L)).thenReturn(List.of("t1"));
    when(logRepository.saveAndFlush(any())).thenThrow(new DataIntegrityViolationException("dup"));

    listener.notifyLocality(7L);

    verify(fcmSender, never())
        .sendMulticast(anyList(), anyString(), anyString(), anyMap(), anyString());
  }

  @Test
  void sendsInBatchesClearsStaleTokensAndRecordsCounts() {
    when(bulletinRepository.findByIdWithLocality(7L)).thenReturn(Optional.of(bulletin()));
    List<String> tokens = new ArrayList<>();
    for (int i = 0; i < 501; i++) {
      tokens.add("t" + i);
    }
    when(sessionRepository.findActiveNotificationTokensByLocalityId(1L)).thenReturn(tokens);
    NotificationLogEntity saved = NotificationLogEntity.builder().id(99L).build();
    when(logRepository.saveAndFlush(any())).thenReturn(saved);
    when(fcmSender.sendMulticast(anyList(), anyString(), anyString(), anyMap(), anyString()))
        .thenReturn(new MulticastResult(Map.of("a", "m1"), List.of("stale")));

    listener.notifyLocality(7L);

    verify(fcmSender, org.mockito.Mockito.times(2))
        .sendMulticast(
            anyList(),
            eq("Daily Bulletin — Koramangala"),
            eq("Light showers expected after 5 PM."),
            anyMap(),
            eq("bulletin_system"));
    verify(sessionRepository, org.mockito.Mockito.times(2)).clearNotificationToken("stale");
    assertEquals(501, saved.getRecipientCount());
    assertEquals(2, saved.getSuccessCount());
  }

  @Test
  void claimRowFollowsDocumentedAccessPattern() {
    when(bulletinRepository.findByIdWithLocality(7L)).thenReturn(Optional.of(bulletin()));
    when(sessionRepository.findActiveNotificationTokensByLocalityId(1L)).thenReturn(List.of("t1"));
    when(logRepository.saveAndFlush(any()))
        .thenReturn(NotificationLogEntity.builder().id(99L).build());
    when(fcmSender.sendMulticast(anyList(), anyString(), anyString(), anyMap(), anyString()))
        .thenReturn(new MulticastResult(Map.of(), List.of()));

    listener.notifyLocality(7L);

    org.mockito.ArgumentCaptor<NotificationLogEntity> captor =
        org.mockito.ArgumentCaptor.forClass(NotificationLogEntity.class);
    verify(logRepository).saveAndFlush(captor.capture());
    NotificationLogEntity row = captor.getValue();
    assertEquals(NotificationType.AUTO_CHAT, row.getType());
    assertEquals(NotificationSubType.CHAT_BULLETIN, row.getSubType());
    assertEquals(SourceRefType.BULLETIN, row.getSourceRefType());
    assertEquals(7L, row.getSourceRefId());
  }
}
