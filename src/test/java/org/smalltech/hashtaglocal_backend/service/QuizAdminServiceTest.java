package org.smalltech.hashtaglocal_backend.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.smalltech.hashtaglocal_backend.entity.BulletinEntity;
import org.smalltech.hashtaglocal_backend.entity.Locality;
import org.smalltech.hashtaglocal_backend.entity.PeriodicDataEntity;
import org.smalltech.hashtaglocal_backend.model.response.AdminBulletinData;
import org.smalltech.hashtaglocal_backend.repository.BulletinRepository;
import org.smalltech.hashtaglocal_backend.repository.FeedPostRepository;
import org.smalltech.hashtaglocal_backend.repository.LocalityRepository;
import org.smalltech.hashtaglocal_backend.repository.QuizAttemptRepository;
import org.smalltech.hashtaglocal_backend.repository.QuizRepository;
import org.smalltech.hashtaglocal_backend.repository.UserRepository;

/**
 * The bulletin list for a date includes every saved-user locality, so one whose weather fetch
 * failed shows up with empty weather instead of disappearing from the ops portal.
 */
@ExtendWith(MockitoExtension.class)
class QuizAdminServiceTest {

  private static final LocalDate DATE = LocalDate.of(2026, 10, 20);

  @Mock private QuizRepository quizRepository;
  @Mock private QuizAttemptRepository quizAttemptRepository;
  @Mock private BulletinRepository bulletinRepository;
  @Mock private LocalityRepository localityRepository;
  @Mock private UserRepository userRepository;
  @Mock private FeedPostRepository feedPostRepository;
  @Mock private GroqClient groqClient;

  @InjectMocks private QuizAdminService service;

  private final Locality portBlair =
      Locality.builder().id(5L).hashtag("portblair").name("Port Blair").build();
  private final Locality kargil =
      Locality.builder().id(3L).hashtag("kargil").name("Kargil").build();

  @Test
  void listForDateIncludesLocalitiesWithoutABulletin() {
    when(bulletinRepository.findByDateOrderByLocalityId(DATE))
        .thenReturn(List.of(bulletin(kargil)));
    when(userRepository.findDistinctUserLocalities()).thenReturn(List.of(portBlair, kargil));

    List<AdminBulletinData> rows = service.listBulletins(null, DATE);

    assertEquals(2, rows.size());
    AdminBulletinData generated = rows.get(0);
    assertEquals(3L, generated.getLocalityId());
    assertNotNull(generated.getId());
    assertEquals(13.0, generated.getWeather().get("max_temp"));

    AdminBulletinData missing = rows.get(1);
    assertEquals(5L, missing.getLocalityId());
    assertEquals("Port Blair", missing.getLocalityName());
    assertEquals("portblair", missing.getHashtag());
    assertEquals(DATE, missing.getDate());
    assertNull(missing.getId());
    assertNull(missing.getWeather());
    assertNull(missing.getSummary());
  }

  @Test
  void listForDateAndLocalityReturnsEmptyRowWhenMissing() {
    when(bulletinRepository.findByLocalityIdAndDate(5L, DATE))
        .thenReturn(java.util.Optional.empty());
    when(userRepository.findDistinctUserLocalities()).thenReturn(List.of(portBlair, kargil));

    List<AdminBulletinData> rows = service.listBulletins(5L, DATE);

    assertEquals(1, rows.size());
    assertEquals(5L, rows.get(0).getLocalityId());
    assertNull(rows.get(0).getWeather());
  }

  @Test
  void listWithoutDateIsUnchanged() {
    when(bulletinRepository.findByLocalityIdOrderByDateDesc(3L))
        .thenReturn(List.of(bulletin(kargil)));

    List<AdminBulletinData> rows = service.listBulletins(3L, null);

    assertEquals(1, rows.size());
    verify(userRepository, never()).findDistinctUserLocalities();
  }

  private BulletinEntity bulletin(Locality locality) {
    return BulletinEntity.builder()
        .id(100L)
        .locality(locality)
        .date(DATE)
        .periodicData(
            PeriodicDataEntity.builder()
                .data(Map.of("min_temp", 1.0, "max_temp", 13.0))
                .source("OPEN_METEO")
                .build())
        .summary(Map.of("text", "Today: 1°C to 13°C, 0% chance of rain."))
        .build();
  }
}
