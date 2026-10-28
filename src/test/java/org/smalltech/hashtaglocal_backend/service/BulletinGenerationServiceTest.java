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
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.smalltech.hashtaglocal_backend.entity.Locality;
import org.smalltech.hashtaglocal_backend.repository.BulletinRepository;
import org.smalltech.hashtaglocal_backend.repository.FeedPostRepository;
import org.smalltech.hashtaglocal_backend.repository.LocationRepository;
import org.smalltech.hashtaglocal_backend.repository.PeriodicDataRepository;
import org.smalltech.hashtaglocal_backend.repository.UserRepository;
import org.smalltech.hashtaglocal_backend.service.weather.WeatherProvider;
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

  private org.smalltech.hashtaglocal_backend.entity.PeriodicDataEntity mockPeriodicData() {
    return org.smalltech.hashtaglocal_backend.entity.PeriodicDataEntity.builder().id(1L).build();
  }
}
