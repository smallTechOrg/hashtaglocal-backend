package org.smalltech.hashtaglocal_backend.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.Point;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.smalltech.hashtaglocal_backend.entity.BulletinEntity;
import org.smalltech.hashtaglocal_backend.entity.Locality;
import org.smalltech.hashtaglocal_backend.entity.Location;
import org.smalltech.hashtaglocal_backend.repository.BulletinRepository;
import org.smalltech.hashtaglocal_backend.repository.FeedPostRepository;
import org.smalltech.hashtaglocal_backend.repository.LocationRepository;
import org.smalltech.hashtaglocal_backend.repository.PeriodicDataRepository;
import org.smalltech.hashtaglocal_backend.repository.UserRepository;
import org.smalltech.hashtaglocal_backend.service.weather.WeatherProvider;
import org.smalltech.hashtaglocal_backend.service.weather.WeatherSnapshot;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * Covers the failure-alert email added on top of {@link BulletinGenerationService}: sent only when
 * at least one locality fails, includes the failure detail, and never breaks the run if SMTP itself
 * is down.
 */
@ExtendWith(MockitoExtension.class)
class BulletinGenerationServiceTest {

  @Mock private UserRepository userRepository;
  @Mock private LocationRepository locationRepository;
  @Mock private PeriodicDataRepository periodicDataRepository;
  @Mock private BulletinRepository bulletinRepository;
  @Mock private FeedPostRepository feedPostRepository;
  @Mock private WeatherProvider weatherProvider;
  @Mock private GroqClient groqClient;
  @Mock private FeedService feedService;
  @Mock private JavaMailSender mailSender;

  private BulletinGenerationService service;

  @BeforeEach
  void setUp() {
    service =
        new BulletinGenerationService(
            userRepository,
            locationRepository,
            periodicDataRepository,
            bulletinRepository,
            feedPostRepository,
            weatherProvider,
            groqClient,
            feedService,
            mailSender);
    ReflectionTestUtils.setField(service, "alertAdminEmail", "admin@example.com");
    ReflectionTestUtils.setField(service, "alertFromEmail", "noreply@example.com");
  }

  @Test
  void sendsFailureAlertEmailWhenALocalityFails() {
    Locality okLocality = Locality.builder().id(1L).hashtag("okcity").name("OK City").build();
    Locality badLocality = Locality.builder().id(2L).hashtag("badcity").name("Bad City").build();
    when(userRepository.findDistinctUserLocalities()).thenReturn(List.of(okLocality, badLocality));

    // okLocality already has today's weather -> generateForLocality short-circuits as "skipped".
    when(periodicDataRepository.findByLocalityIdAndDateAndDataType(eq(1L), any(), eq("WEATHER")))
        .thenReturn(Optional.of(mockPeriodicData()));
    when(bulletinRepository.findByLocalityIdAndDate(eq(1L), any())).thenReturn(Optional.empty());

    // badLocality has no coordinates resolvable -> generateForLocality throws.
    when(periodicDataRepository.findByLocalityIdAndDateAndDataType(eq(2L), any(), eq("WEATHER")))
        .thenReturn(Optional.empty());
    when(locationRepository.findFirstByLocalityId(2L)).thenReturn(Optional.empty());

    BulletinGenerationService.GenerationResult result = service.generateForAllUserLocalities();

    assertEquals(2, result.getTotalLocalities());
    assertEquals(0, result.getGenerated());
    assertEquals(1, result.getSkipped());
    assertEquals(1, result.getFailed());

    ArgumentCaptor<SimpleMailMessage> captor = ArgumentCaptor.forClass(SimpleMailMessage.class);
    verify(mailSender).send(captor.capture());
    SimpleMailMessage sent = captor.getValue();
    assertEquals("admin@example.com", sent.getTo()[0]);
    assertTrue(sent.getSubject() != null && sent.getSubject().contains("1/2"));
    assertTrue(sent.getText() != null && sent.getText().contains("badcity"));
  }

  @Test
  void doesNotSendAlertWhenNothingFails() {
    Locality okLocality = Locality.builder().id(1L).hashtag("okcity").name("OK City").build();
    when(userRepository.findDistinctUserLocalities()).thenReturn(List.of(okLocality));
    when(periodicDataRepository.findByLocalityIdAndDateAndDataType(eq(1L), any(), eq("WEATHER")))
        .thenReturn(Optional.of(mockPeriodicData()));
    when(bulletinRepository.findByLocalityIdAndDate(eq(1L), any())).thenReturn(Optional.empty());

    BulletinGenerationService.GenerationResult result = service.generateForAllUserLocalities();

    assertEquals(0, result.getFailed());
    verify(mailSender, never()).send(any(SimpleMailMessage.class));
  }

  @Test
  void runStillCompletesWhenAlertEmailItselfFails() {
    Locality badLocality = Locality.builder().id(2L).hashtag("badcity").name("Bad City").build();
    when(userRepository.findDistinctUserLocalities()).thenReturn(List.of(badLocality));
    when(periodicDataRepository.findByLocalityIdAndDateAndDataType(eq(2L), any(), eq("WEATHER")))
        .thenReturn(Optional.empty());
    when(locationRepository.findFirstByLocalityId(2L)).thenReturn(Optional.empty());
    doThrow(new MailException("smtp down") {}).when(mailSender).send(any(SimpleMailMessage.class));

    BulletinGenerationService.GenerationResult result = service.generateForAllUserLocalities();

    assertEquals(1, result.getFailed());
  }

  @Test
  void countsAndAlertsOnFallbackSummaries() {
    Locality locality =
        stubGeneration("satara", new GroqClient.WeatherSummary("Today: 21°C.", true));
    when(userRepository.findDistinctUserLocalities()).thenReturn(List.of(locality));

    BulletinGenerationService.GenerationResult result = service.generateForAllUserLocalities();

    assertEquals(1, result.getGenerated());
    assertEquals(1, result.getFallbackSummaries());
    assertEquals(0, result.getFailed());

    ArgumentCaptor<SimpleMailMessage> captor = ArgumentCaptor.forClass(SimpleMailMessage.class);
    verify(mailSender).send(captor.capture());
    SimpleMailMessage sent = captor.getValue();
    assertEquals(
        "#local bulletin: 1/1 localities got the fallback summary (" + result.getDate() + ")",
        sent.getSubject());
    assertTrue(sent.getText() != null && sent.getText().contains("satara"));
  }

  @Test
  void aiSummaryIsNotCountedAsFallback() {
    Locality locality =
        stubGeneration("satara", new GroqClient.WeatherSummary("Grab a raincoat.", false));
    when(userRepository.findDistinctUserLocalities()).thenReturn(List.of(locality));

    BulletinGenerationService.GenerationResult result = service.generateForAllUserLocalities();

    assertEquals(1, result.getGenerated());
    assertEquals(0, result.getFallbackSummaries());
    verify(mailSender, never()).send(any(SimpleMailMessage.class));
  }

  private Locality stubGeneration(String hashtag, GroqClient.WeatherSummary summary) {
    Locality locality = Locality.builder().id(3L).hashtag(hashtag).name("Satara").build();
    Point point = new GeometryFactory().createPoint(new Coordinate(74.0, 17.7));
    when(periodicDataRepository.findByLocalityIdAndDateAndDataType(eq(3L), any(), eq("WEATHER")))
        .thenReturn(Optional.empty());
    when(locationRepository.findFirstByLocalityId(3L))
        .thenReturn(Optional.of(Location.builder().point(point).build()));
    when(weatherProvider.fetchDaily(17.7, 74.0))
        .thenReturn(WeatherSnapshot.builder().source("OPEN_METEO").build());
    when(periodicDataRepository.save(any())).thenReturn(mockPeriodicData());
    when(bulletinRepository.findRecentWeatherSummaries(3L, 5)).thenReturn(List.of());
    when(groqClient.generateWeatherSummary(eq("Satara"), any(), any())).thenReturn(summary);
    when(bulletinRepository.findByLocalityIdAndDate(eq(3L), any())).thenReturn(Optional.empty());
    when(bulletinRepository.save(any()))
        .thenReturn(BulletinEntity.builder().id(9L).locality(locality).build());
    return locality;
  }

  private org.smalltech.hashtaglocal_backend.entity.PeriodicDataEntity mockPeriodicData() {
    return org.smalltech.hashtaglocal_backend.entity.PeriodicDataEntity.builder().id(1L).build();
  }
}
