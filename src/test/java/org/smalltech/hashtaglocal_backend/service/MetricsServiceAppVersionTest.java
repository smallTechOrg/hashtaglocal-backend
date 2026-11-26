package org.smalltech.hashtaglocal_backend.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.smalltech.hashtaglocal_backend.entity.AppVersionSeenEntity;
import org.smalltech.hashtaglocal_backend.repository.AppVersionSeenRepository;
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

  private static final int YEAR = 2026;
  private static final int WEEK = 48;

  @Mock private UserRepository userRepository;
  @Mock private IssueRepository issueRepository;
  @Mock private IssueActionRepository issueActionRepository;
  @Mock private QuizAttemptRepository quizAttemptRepository;
  @Mock private NotificationLogRepository notificationLogRepository;
  @Mock private UserAuthSessionRepository userAuthSessionRepository;
  @Mock private EventApprovalRepository eventApprovalRepository;
  @Mock private EventRepository eventRepository;
  @Mock private FeedPostRepository feedPostRepository;
  @Mock private AppVersionSeenRepository appVersionSeenRepository;

  @InjectMocks private MetricsService metricsService;

  private static AppVersionSeenEntity seen(long userId, String version) {
    return AppVersionSeenEntity.builder()
        .sessionId(userId * 10)
        .userId(userId)
        .appVersion(version)
        .weekYear(YEAR)
        .weekNumber(WEEK)
        .lastSeenAt(LocalDateTime.of(2026, 11, 24, 10, 0))
        .build();
  }

  @Test
  void noVersionsEverSeen_givesNoData() {
    when(appVersionSeenRepository.findVersionsSeenUpTo(YEAR, WEEK)).thenReturn(List.of());

    MetricsService.AppVersionWeek week = metricsService.appVersionWeek(YEAR, WEEK);

    assertNull(week.latest());
    assertNull(week.percentOf(0));
  }

  @Test
  void latestIsHighestVersion_andOutdatedIsTwoOrMoreReleasesBehind() {
    // Returned unordered on purpose: 1.0.10 must sort above 1.0.9.
    when(appVersionSeenRepository.findVersionsSeenUpTo(YEAR, WEEK))
        .thenReturn(List.of("1.0.9", "1.0.10", "1.0.8", "1.0.7"));
    when(appVersionSeenRepository.findByWeekYearAndWeekNumber(YEAR, WEEK))
        .thenReturn(
            List.of(
                seen(1, "1.0.10"), // latest
                seen(2, "1.0.9"), // one behind: not outdated
                seen(3, "1.0.8"), // two behind: outdated
                seen(4, "1.0.7"))); // three behind: outdated

    MetricsService.AppVersionWeek week = metricsService.appVersionWeek(YEAR, WEEK);

    assertEquals("1.0.10", week.latest());
    assertEquals(1, week.onLatestCount());
    assertEquals(25, week.percentOf(week.onLatestCount()));
    assertEquals(2, week.outdated().size());
    assertEquals(50, week.percentOf(week.outdated().size()));
  }

  @Test
  void userWithTwoDevicesCountsOnceOnTheNewerVersion() {
    when(appVersionSeenRepository.findVersionsSeenUpTo(YEAR, WEEK))
        .thenReturn(List.of("1.0.0", "1.1.0", "1.2.0"));
    when(appVersionSeenRepository.findByWeekYearAndWeekNumber(YEAR, WEEK))
        .thenReturn(List.of(seen(1, "1.0.0"), seen(1, "1.2.0")));

    MetricsService.AppVersionWeek week = metricsService.appVersionWeek(YEAR, WEEK);

    assertEquals(1, week.perUser().size());
    assertEquals(100, week.percentOf(week.onLatestCount()));
    assertEquals(0, week.outdated().size());
  }

  @Test
  void weekWithNoUsers_stillReportsLatestButNoPercentages() {
    when(appVersionSeenRepository.findVersionsSeenUpTo(YEAR, WEEK)).thenReturn(List.of("1.0.0"));
    when(appVersionSeenRepository.findByWeekYearAndWeekNumber(YEAR, WEEK)).thenReturn(List.of());

    MetricsService.AppVersionWeek week = metricsService.appVersionWeek(YEAR, WEEK);

    assertEquals("1.0.0", week.latest());
    assertNull(week.percentOf(week.onLatestCount()));
  }
}
