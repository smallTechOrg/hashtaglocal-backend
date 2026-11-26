package org.smalltech.hashtaglocal_backend.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.smalltech.hashtaglocal_backend.dto.AppVersionsResponse;
import org.smalltech.hashtaglocal_backend.entity.UserAuthSessionEntity;
import org.smalltech.hashtaglocal_backend.entity.UserEntity;
import org.smalltech.hashtaglocal_backend.model.Platform;
import org.smalltech.hashtaglocal_backend.repository.EventApprovalRepository;
import org.smalltech.hashtaglocal_backend.repository.EventRepository;
import org.smalltech.hashtaglocal_backend.repository.FeedPostRepository;
import org.smalltech.hashtaglocal_backend.repository.IssueActionRepository;
import org.smalltech.hashtaglocal_backend.repository.IssueRepository;
import org.smalltech.hashtaglocal_backend.repository.NotificationLogRepository;
import org.smalltech.hashtaglocal_backend.repository.QuizAttemptRepository;
import org.smalltech.hashtaglocal_backend.repository.UserAuthSessionRepository;
import org.smalltech.hashtaglocal_backend.repository.UserRepository;

@ExtendWith(MockitoExtension.class)
class MetricsServiceAppVersionTest {

  private static final LocalDateTime NOW = LocalDateTime.of(2026, 11, 26, 10, 0);

  @Mock private UserRepository userRepository;
  @Mock private IssueRepository issueRepository;
  @Mock private IssueActionRepository issueActionRepository;
  @Mock private QuizAttemptRepository quizAttemptRepository;
  @Mock private NotificationLogRepository notificationLogRepository;
  @Mock private UserAuthSessionRepository userAuthSessionRepository;
  @Mock private EventApprovalRepository eventApprovalRepository;
  @Mock private EventRepository eventRepository;
  @Mock private FeedPostRepository feedPostRepository;

  @InjectMocks private MetricsService metricsService;

  private static UserEntity user(long id, String username) {
    return UserEntity.builder().id(id).username(username).build();
  }

  private static UserAuthSessionEntity session(
      UserEntity user, String version, Platform platform, LocalDateTime updatedAt) {
    return UserAuthSessionEntity.builder()
        .user(user)
        .appVersion(version)
        .platform(platform)
        .updatedAt(updatedAt)
        .build();
  }

  @Test
  void countsEachUserOnceOnTheirNewestVersion() {
    UserEntity asha = user(1, "asha");
    UserEntity rahul = user(2, "rahul");
    UserEntity meera = user(3, "meera");
    when(userAuthSessionRepository.findAllWithAppVersion())
        .thenReturn(
            List.of(
                // Asha has an old phone on 1.0.2 and a new one on 1.0.10: she counts on 1.0.10.
                session(asha, "1.0.2", Platform.IOS, NOW.minusDays(1)),
                session(asha, "1.0.10", Platform.ANDROID, NOW.minusDays(3)),
                session(rahul, "1.0.2", Platform.IOS, NOW.minusDays(2)),
                session(meera, "1.0.10", Platform.ANDROID, NOW.minusDays(5))));

    AppVersionsResponse r = metricsService.getAppVersions();

    assertEquals("1.0.10", r.getLatestVersion());
    assertEquals(3, r.getTotalUsers());

    assertEquals(2, r.getVersions().size());
    assertEquals("1.0.10", r.getVersions().get(0).getVersion());
    assertEquals(2, r.getVersions().get(0).getUsers());
    assertEquals(67, r.getVersions().get(0).getPercent());
    assertEquals("1.0.2", r.getVersions().get(1).getVersion());
    assertEquals(33, r.getVersions().get(1).getPercent());

    // Most recently active first; Asha's last activity is her newer session on either device.
    AppVersionsResponse.UserVersion first = r.getUsers().get(0);
    assertEquals("asha", first.getUsername());
    assertEquals("1.0.10", first.getAppVersion());
    assertEquals("ANDROID", first.getPlatform());
    assertTrue(first.getLastActive().startsWith("2026-11-25"));
    assertEquals("rahul", r.getUsers().get(1).getUsername());
    assertEquals("meera", r.getUsers().get(2).getUsername());
  }

  @Test
  void isEmptyWhenNoAppHasReportedAVersion() {
    when(userAuthSessionRepository.findAllWithAppVersion()).thenReturn(List.of());

    AppVersionsResponse r = metricsService.getAppVersions();

    assertNull(r.getLatestVersion());
    assertEquals(0, r.getTotalUsers());
    assertTrue(r.getVersions().isEmpty());
    assertTrue(r.getUsers().isEmpty());
  }
}
