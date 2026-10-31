package org.smalltech.hashtaglocal_backend.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.function.BooleanSupplier;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.PrecisionModel;
import org.smalltech.hashtaglocal_backend.entity.BulletinEntity;
import org.smalltech.hashtaglocal_backend.entity.Locality;
import org.smalltech.hashtaglocal_backend.entity.Location;
import org.smalltech.hashtaglocal_backend.entity.NotificationLogEntity;
import org.smalltech.hashtaglocal_backend.entity.UserAuthProviderEntity;
import org.smalltech.hashtaglocal_backend.entity.UserAuthSessionEntity;
import org.smalltech.hashtaglocal_backend.entity.UserEntity;
import org.smalltech.hashtaglocal_backend.infra.notification.FcmSender;
import org.smalltech.hashtaglocal_backend.infra.notification.FcmSender.MulticastResult;
import org.smalltech.hashtaglocal_backend.model.NotificationSubType;
import org.smalltech.hashtaglocal_backend.model.NotificationType;
import org.smalltech.hashtaglocal_backend.model.SourceRefType;
import org.smalltech.hashtaglocal_backend.repository.BulletinRepository;
import org.smalltech.hashtaglocal_backend.repository.LocalityRepository;
import org.smalltech.hashtaglocal_backend.repository.LocationRepository;
import org.smalltech.hashtaglocal_backend.repository.NotificationLogRepository;
import org.smalltech.hashtaglocal_backend.repository.UserAuthProviderRepository;
import org.smalltech.hashtaglocal_backend.repository.UserAuthSessionRepository;
import org.smalltech.hashtaglocal_backend.repository.UserRepository;
import org.smalltech.hashtaglocal_backend.service.BulletinGenerationService;
import org.smalltech.hashtaglocal_backend.service.GroqClient;
import org.smalltech.hashtaglocal_backend.service.weather.WeatherProvider;
import org.smalltech.hashtaglocal_backend.service.weather.WeatherSnapshot;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

/**
 * End-to-end: real generation service → real event → real async listener → real Postgres. Only the
 * outside world (FCM, weather, Groq) is mocked.
 */
@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = "bulletin.notification.enabled=true")
@ActiveProfiles("test")
class BulletinNotificationIntegrationTest {

  private static final ZoneId IST = ZoneId.of("Asia/Kolkata");
  private static final String SUMMARY = "Light showers expected after 5 PM. Carry an umbrella.";

  @Autowired private BulletinGenerationService generationService;
  @Autowired private NotificationLogRepository notificationLogRepository;
  @Autowired private BulletinRepository bulletinRepository;
  @Autowired private LocalityRepository localityRepository;
  @Autowired private LocationRepository locationRepository;
  @Autowired private UserRepository userRepository;
  @Autowired private UserAuthProviderRepository providerRepository;
  @Autowired private UserAuthSessionRepository sessionRepository;
  @Autowired private JdbcTemplate jdbc;

  @MockBean private FcmSender fcmSender;
  @MockBean private WeatherProvider weatherProvider;
  @MockBean private GroqClient groqClient;

  private Locality locality;
  private String token;

  @BeforeEach
  void setUp() {
    reset(fcmSender, weatherProvider, groqClient);

    // create-drop does not build the partial unique index from the migration script
    jdbc.execute(
        "CREATE UNIQUE INDEX IF NOT EXISTS uq_notification_log_bulletin ON notification_log"
            + " (source_ref_id) WHERE type = 'AUTO_CHAT' AND sub_type = 'CHAT_BULLETIN'"
            + " AND source_ref_type = 'BULLETIN'");

    when(weatherProvider.fetchDaily(anyDouble(), anyDouble()))
        .thenReturn(WeatherSnapshot.builder().minTemp(21.0).maxTemp(28.0).source("TEST").build());
    when(groqClient.generateWeatherSummary(
            anyString(), org.mockito.ArgumentMatchers.any(), anyList()))
        .thenReturn(SUMMARY);
    when(fcmSender.sendMulticast(anyList(), anyString(), anyString(), anyMap(), anyString()))
        .thenAnswer(
            inv -> {
              List<String> tokens = inv.getArgument(0);
              return new MulticastResult(Map.of(tokens.get(0), "msg-1"), List.of());
            });

    String unique = "bn" + System.nanoTime();
    locality =
        localityRepository.save(
            Locality.builder().hashtag("#" + unique).name("Koramangala " + unique).build());
    Location location =
        locationRepository.save(
            Location.builder()
                .point(
                    new GeometryFactory(new PrecisionModel(), 4326)
                        .createPoint(new Coordinate(77.62, 12.93)))
                .locality(locality)
                .name("Test spot")
                .build());
    UserEntity user =
        userRepository.save(
            UserEntity.builder().username(unique).locale("en_US").location(location).build());
    UserAuthProviderEntity provider =
        providerRepository.save(
            UserAuthProviderEntity.builder()
                .user(user)
                .providerType("TEST")
                .providerUserId(unique)
                .email(unique + "@example.com")
                .build());
    token = "fcm-token-" + unique;
    long expiry = System.currentTimeMillis() / 1000 + 3600;
    sessionRepository.save(
        UserAuthSessionEntity.builder()
            .user(user)
            .userAuthProvider(provider)
            .accessToken("a-" + unique)
            .accessTokenExpiryTs(expiry)
            .refreshToken("r-" + unique)
            .refreshTokenExpiryTs(expiry + 7200)
            .notificationToken(token)
            .build());
  }

  @Test
  void todaysBulletinSendsExactlyOnePush_withDocumentedAccessPattern() throws Exception {
    LocalDate today = LocalDate.now(IST);

    assertTrue(generationService.generateForLocality(locality, today));

    BulletinEntity bulletin =
        bulletinRepository.findByLocalityIdAndDate(locality.getId(), today).orElseThrow();
    Long bulletinId = bulletin.getId();

    // The documented access pattern finds the claim row (async listener → poll).
    awaitTrue(
        () ->
            notificationLogRepository.existsByTypeAndSubTypeAndSourceRefTypeAndSourceRefId(
                NotificationType.AUTO_CHAT,
                NotificationSubType.CHAT_BULLETIN,
                SourceRefType.BULLETIN,
                bulletinId));
    // ... and the counts are written once sending finishes.
    awaitTrue(() -> logRow(bulletinId).getSuccessCount() != null);

    NotificationLogEntity row = logRow(bulletinId);
    assertEquals("Daily Bulletin — " + locality.getName(), row.getTitle());
    assertEquals("Light showers expected after 5 PM.", row.getBody());
    assertEquals(1, row.getRecipientCount());
    assertEquals(1, row.getSuccessCount());

    verify(fcmSender, times(1))
        .sendMulticast(
            eq(List.of(token)),
            eq("Daily Bulletin — " + locality.getName()),
            eq("Light showers expected after 5 PM."),
            org.mockito.ArgumentMatchers.argThat(
                data ->
                    "BULLETIN".equals(data.get("type"))
                        && bulletinId.toString().equals(data.get("bulletinId"))
                        && locality.getId().toString().equals(data.get("localityId"))
                        && data.containsKey("notificationLogId")),
            eq("bulletin_system"));

    // Re-running generation must not send again (regeneration → no duplicate).
    generationService.generateForLocality(locality, today);
    Thread.sleep(1500);
    verify(fcmSender, times(1))
        .sendMulticast(anyList(), anyString(), anyString(), anyMap(), anyString());
    assertEquals(1, countLogRows(bulletinId));

    // The DB-level guard rejects a second claim for the same bulletin.
    assertThrows(
        DataIntegrityViolationException.class,
        () ->
            notificationLogRepository.saveAndFlush(
                NotificationLogEntity.builder()
                    .source(row.getSource())
                    .sourceRefType(SourceRefType.BULLETIN)
                    .sourceRefId(bulletinId)
                    .type(NotificationType.AUTO_CHAT)
                    .subType(NotificationSubType.CHAT_BULLETIN)
                    .title("dup")
                    .body("dup")
                    .payload("{}")
                    .build()));
  }

  @Test
  void oldBulletinDoesNotSendAPush() throws Exception {
    LocalDate yesterday = LocalDate.now(IST).minusDays(1);

    generationService.generateForLocality(locality, yesterday);
    Thread.sleep(1500);

    Long bulletinId =
        bulletinRepository
            .findByLocalityIdAndDate(locality.getId(), yesterday)
            .orElseThrow()
            .getId();
    assertFalse(
        notificationLogRepository.existsByTypeAndSubTypeAndSourceRefTypeAndSourceRefId(
            NotificationType.AUTO_CHAT,
            NotificationSubType.CHAT_BULLETIN,
            SourceRefType.BULLETIN,
            bulletinId));
    verify(fcmSender, times(0))
        .sendMulticast(anyList(), anyString(), anyString(), anyMap(), anyString());
  }

  @Test
  void localityWithNoDeviceTokensSendsNothingAndLogsNothing() throws Exception {
    jdbc.update(
        "UPDATE user_auth_sessions SET notification_token = NULL WHERE notification_token = ?",
        token);
    LocalDate today = LocalDate.now(IST);

    generationService.generateForLocality(locality, today);
    Thread.sleep(1500);

    Long bulletinId =
        bulletinRepository.findByLocalityIdAndDate(locality.getId(), today).orElseThrow().getId();
    assertEquals(0, countLogRows(bulletinId));
    verify(fcmSender, times(0))
        .sendMulticast(anyList(), anyString(), anyString(), anyMap(), anyString());
  }

  private NotificationLogEntity logRow(Long bulletinId) {
    return notificationLogRepository.findAll().stream()
        .filter(n -> bulletinId.equals(n.getSourceRefId()))
        .filter(n -> n.getSourceRefType() == SourceRefType.BULLETIN)
        .findFirst()
        .orElseThrow();
  }

  private int countLogRows(Long bulletinId) {
    return (int)
        notificationLogRepository.findAll().stream()
            .filter(n -> bulletinId.equals(n.getSourceRefId()))
            .filter(n -> n.getSourceRefType() == SourceRefType.BULLETIN)
            .count();
  }

  private static void awaitTrue(BooleanSupplier condition) throws InterruptedException {
    long deadline = System.currentTimeMillis() + 10_000;
    while (System.currentTimeMillis() < deadline) {
      try {
        if (condition.getAsBoolean()) {
          return;
        }
      } catch (RuntimeException ignored) {
        // row not there yet
      }
      Thread.sleep(100);
    }
    throw new AssertionError("condition not met within 10s");
  }
}
