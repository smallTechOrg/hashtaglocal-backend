package org.smalltech.hashtaglocal_backend.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.smalltech.hashtaglocal_backend.entity.BulletinEntity;
import org.smalltech.hashtaglocal_backend.entity.Locality;
import org.smalltech.hashtaglocal_backend.entity.NotificationLogEntity;
import org.smalltech.hashtaglocal_backend.entity.PeriodicDataEntity;
import org.smalltech.hashtaglocal_backend.infra.notification.FcmSender;
import org.smalltech.hashtaglocal_backend.infra.notification.FcmSender.MulticastResult;
import org.smalltech.hashtaglocal_backend.model.NotificationType;
import org.smalltech.hashtaglocal_backend.repository.BulletinRepository;
import org.smalltech.hashtaglocal_backend.repository.NotificationLogRepository;
import org.smalltech.hashtaglocal_backend.repository.UserAuthSessionRepository;
import org.smalltech.hashtaglocal_backend.repository.UserRepository;
import org.springframework.beans.factory.ObjectProvider;

@ExtendWith(MockitoExtension.class)
class BulletinNotificationServiceTest {

  @Mock UserRepository userRepository;
  @Mock BulletinRepository bulletinRepository;
  @Mock BulletinGenerationService bulletinGenerationService;
  @Mock UserAuthSessionRepository userAuthSessionRepository;
  @Mock NotificationLogRepository notificationLogRepository;
  @Mock FcmSender fcmSender;
  @Mock ObjectProvider<BulletinNotificationService> selfProvider;

  private BulletinNotificationService service;
  private Locality locality;

  @BeforeEach
  void setUp() {
    service =
        new BulletinNotificationService(
            userRepository,
            bulletinRepository,
            bulletinGenerationService,
            userAuthSessionRepository,
            notificationLogRepository,
            fcmSender,
            new ObjectMapper(),
            selfProvider);
    when(selfProvider.getObject()).thenReturn(service);
    locality = Locality.builder().id(10L).hashtag("#tnagar").name("T Nagar").build();
    when(userRepository.findDistinctUserLocalities()).thenReturn(List.of(locality));
  }

  private BulletinEntity readyBulletin(String text) {
    return BulletinEntity.builder()
        .id(5L)
        .locality(locality)
        .date(LocalDate.now())
        .periodicData(PeriodicDataEntity.builder().id(7L).build())
        .summary(Map.of("text", text))
        .build();
  }

  private void stubSend(BulletinEntity bulletin, List<String> tokens) {
    when(notificationLogRepository.existsBySourceRefTypeAndSourceRefId("BULLETIN", 5L))
        .thenReturn(false);
    when(userAuthSessionRepository.findActiveNotificationTokensByLocalityId(10L))
        .thenReturn(tokens);
    when(bulletinRepository.findById(5L)).thenReturn(Optional.of(bulletin));
    when(notificationLogRepository.save(any()))
        .thenAnswer(
            inv -> {
              NotificationLogEntity e = inv.getArgument(0);
              if (e.getId() == null) e.setId(99L);
              return e;
            });
  }

  @Test
  @SuppressWarnings("unchecked")
  void readyBulletinIsPushedToLocalityUsersAndStaleTokensCleared() {
    BulletinEntity bulletin = readyBulletin("Light showers this evening.");
    when(bulletinRepository.findByLocalityIdAndDate(eq(10L), any()))
        .thenReturn(Optional.of(bulletin));
    stubSend(bulletin, List.of("t1", "t2"));
    when(fcmSender.sendMulticast(any(), any(), any(), any(), any()))
        .thenReturn(new MulticastResult(Map.of("t1", "m1"), List.of("t2")));

    var result = service.runCycle();

    ArgumentCaptor<Map<String, String>> data = ArgumentCaptor.forClass(Map.class);
    verify(fcmSender)
        .sendMulticast(
            eq(List.of("t1", "t2")),
            eq("Daily Bulletin — T Nagar"),
            eq("Light showers this evening."),
            data.capture(),
            eq("bulletin_system"));
    assertThat(data.getValue())
        .containsEntry("type", "BULLETIN")
        .containsEntry("bulletinId", "5")
        .containsEntry("localityId", "10")
        .containsEntry("notificationLogId", "99");
    verify(userAuthSessionRepository).clearNotificationToken("t2");
    verify(bulletinGenerationService, never()).generateForLocality(any(), any());

    ArgumentCaptor<NotificationLogEntity> logEntry =
        ArgumentCaptor.forClass(NotificationLogEntity.class);
    verify(notificationLogRepository, times(2)).save(logEntry.capture());
    NotificationLogEntity saved = logEntry.getValue();
    assertThat(saved.getType()).isEqualTo(NotificationType.BULLETIN);
    assertThat(saved.getSourceRefType()).isEqualTo("BULLETIN");
    assertThat(saved.getSourceRefId()).isEqualTo(5L);
    assertThat(saved.getRecipientCount()).isEqualTo(2);
    assertThat(saved.getSuccessCount()).isEqualTo(1);

    assertThat(result.getNotified()).isEqualTo(1);
    assertThat(result.getRegenerated()).isZero();
  }

  @Test
  void missingBulletinIsRegeneratedThenPushed() {
    BulletinEntity bulletin = readyBulletin("Clear skies.");
    when(bulletinRepository.findByLocalityIdAndDate(eq(10L), any()))
        .thenReturn(Optional.empty(), Optional.of(bulletin));
    stubSend(bulletin, List.of("t1"));
    when(fcmSender.sendMulticast(any(), any(), any(), any(), any()))
        .thenReturn(new MulticastResult(Map.of("t1", "m1"), List.of()));

    var result = service.runCycle();

    verify(bulletinGenerationService).generateForLocality(eq(locality), any());
    verify(fcmSender).sendMulticast(eq(List.of("t1")), any(), any(), any(), any());
    assertThat(result.getRegenerated()).isEqualTo(1);
    assertThat(result.getNotified()).isEqualTo(1);
  }

  @Test
  void shellBulletinWithoutWeatherCountsAsMissing() {
    BulletinEntity shell = BulletinEntity.builder().id(5L).locality(locality).build();
    when(bulletinRepository.findByLocalityIdAndDate(eq(10L), any())).thenReturn(Optional.of(shell));

    service.runCycle();

    verify(bulletinGenerationService).generateForLocality(eq(locality), any());
  }

  @Test
  void failedRegenerationSkipsNotification() {
    when(bulletinRepository.findByLocalityIdAndDate(eq(10L), any())).thenReturn(Optional.empty());
    when(bulletinGenerationService.generateForLocality(any(), any()))
        .thenThrow(new IllegalStateException("weather API down"));

    var result = service.runCycle();

    verify(fcmSender, never()).sendMulticast(any(), any(), any(), any(), any());
    verify(notificationLogRepository, never()).save(any());
    assertThat(result.getGenerationFailed()).isEqualTo(1);
    assertThat(result.getNotified()).isZero();
  }

  @Test
  void alreadyNotifiedBulletinIsSkipped() {
    when(bulletinRepository.findByLocalityIdAndDate(eq(10L), any()))
        .thenReturn(Optional.of(readyBulletin("Hot and humid.")));
    when(notificationLogRepository.existsBySourceRefTypeAndSourceRefId("BULLETIN", 5L))
        .thenReturn(true);

    var result = service.runCycle();

    verify(fcmSender, never()).sendMulticast(any(), any(), any(), any(), any());
    assertThat(result.getAlreadyNotified()).isEqualTo(1);
  }

  @Test
  void localityWithoutDevicesWritesNoLog() {
    when(bulletinRepository.findByLocalityIdAndDate(eq(10L), any()))
        .thenReturn(Optional.of(readyBulletin("Hot and humid.")));
    when(userAuthSessionRepository.findActiveNotificationTokensByLocalityId(10L))
        .thenReturn(List.of());

    var result = service.runCycle();

    verify(notificationLogRepository, never()).save(any());
    verify(fcmSender, never()).sendMulticast(any(), any(), any(), any(), any());
    assertThat(result.getNoRecipients()).isEqualTo(1);
  }

  @Test
  void longSummaryIsTruncatedForTheBody() {
    BulletinEntity bulletin = readyBulletin("a".repeat(300));
    when(bulletinRepository.findByLocalityIdAndDate(eq(10L), any()))
        .thenReturn(Optional.of(bulletin));
    stubSend(bulletin, List.of("t1"));
    when(fcmSender.sendMulticast(any(), any(), any(), any(), any()))
        .thenReturn(new MulticastResult(Map.of(), List.of()));

    service.runCycle();

    ArgumentCaptor<String> body = ArgumentCaptor.forClass(String.class);
    verify(fcmSender).sendMulticast(any(), any(), body.capture(), any(), any());
    assertThat(body.getValue()).hasSize(180).endsWith("…");
  }
}
