package org.smalltech.hashtaglocal_backend.service;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.IsoFields;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.smalltech.hashtaglocal_backend.dto.MetricDetailItem;
import org.smalltech.hashtaglocal_backend.dto.MetricDetailResponse;
import org.smalltech.hashtaglocal_backend.dto.OpsSummaryResponse;
import org.smalltech.hashtaglocal_backend.dto.WeeklyMetricPoint;
import org.smalltech.hashtaglocal_backend.dto.WeeklyMetricsResponse;
import org.smalltech.hashtaglocal_backend.entity.IssueEntity;
import org.smalltech.hashtaglocal_backend.entity.Locality;
import org.smalltech.hashtaglocal_backend.entity.Location;
import org.smalltech.hashtaglocal_backend.entity.UserEntity;
import org.smalltech.hashtaglocal_backend.model.EventApprovalStatus;
import org.smalltech.hashtaglocal_backend.repository.EventApprovalRepository;
import org.smalltech.hashtaglocal_backend.repository.EventRepository;
import org.smalltech.hashtaglocal_backend.repository.FeedPostRepository;
import org.smalltech.hashtaglocal_backend.repository.IssueActionRepository;
import org.smalltech.hashtaglocal_backend.repository.IssueRepository;
import org.smalltech.hashtaglocal_backend.repository.NotificationLogRepository;
import org.smalltech.hashtaglocal_backend.repository.QuizAttemptRepository;
import org.smalltech.hashtaglocal_backend.repository.UserAuthSessionRepository;
import org.smalltech.hashtaglocal_backend.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MetricsService {

  private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd");

  private final UserRepository userRepository;
  private final IssueRepository issueRepository;
  private final IssueActionRepository issueActionRepository;
  private final QuizAttemptRepository quizAttemptRepository;
  private final NotificationLogRepository notificationLogRepository;
  private final UserAuthSessionRepository userAuthSessionRepository;
  private final EventApprovalRepository eventApprovalRepository;
  private final EventRepository eventRepository;
  private final FeedPostRepository feedPostRepository;

  public WeeklyMetricsResponse getWeeklyMetrics(
      int fromWeekYear, int fromWeekNumber, int toWeekYear, int toWeekNumber) {

    List<WeeklyMetricPoint> points = new ArrayList<>();
    LocalDate today = LocalDate.now();
    int currentIsoWeek = today.get(IsoFields.WEEK_OF_WEEK_BASED_YEAR);
    int currentIsoYear = today.get(IsoFields.WEEK_BASED_YEAR);

    LocalDate cursor = isoWeekStart(fromWeekYear, fromWeekNumber);
    LocalDate toStart = isoWeekStart(toWeekYear, toWeekNumber);

    while (!cursor.isAfter(toStart)) {
      int year = cursor.get(IsoFields.WEEK_BASED_YEAR);
      int week = cursor.get(IsoFields.WEEK_OF_WEEK_BASED_YEAR);
      boolean isCurrent = (year == currentIsoYear && week == currentIsoWeek);

      LocalDateTime start = cursor.atStartOfDay();
      LocalDateTime end =
          isCurrent ? LocalDateTime.now() : cursor.plusDays(6).atTime(LocalTime.MAX);

      long quizAttempts = quizAttemptRepository.countAttemptsBetween(start, end);
      double dailyAvg = quizAttempts / 7.0;

      points.add(
          WeeklyMetricPoint.builder()
              .weekLabel("W" + week)
              .weekYear(year)
              .weekNumber(week)
              .startDate(cursor.format(DATE_FMT))
              .endDate(cursor.plusDays(6).format(DATE_FMT))
              .isCurrentWeek(isCurrent)
              .newUsers(userRepository.countNewUsersBetween(start, end))
              .activeUsers(userRepository.countActiveUsersBetween(start, end))
              .notificationsSent(notificationLogRepository.sumRecipientCountBetween(start, end))
              .issuesReported(issueRepository.countReportedBetween(start, end))
              .issuesVerified(issueActionRepository.countVerifiedIssuesBetween(start, end))
              .dailyAvgQuiz(Math.round(dailyAvg * 10.0) / 10.0)
              .uniqueQuizUsers(quizAttemptRepository.countDistinctUsersBetween(start, end))
              .build());

      cursor = cursor.plusWeeks(1);
    }

    return WeeklyMetricsResponse.builder().weeks(points).build();
  }

  public MetricDetailResponse getMetricDetail(String metric, int weekYear, int weekNumber) {
    LocalDate weekStart = isoWeekStart(weekYear, weekNumber);
    LocalDate today = LocalDate.now();
    int currentIsoWeek = today.get(IsoFields.WEEK_OF_WEEK_BASED_YEAR);
    int currentIsoYear = today.get(IsoFields.WEEK_BASED_YEAR);
    boolean isCurrent = (weekYear == currentIsoYear && weekNumber == currentIsoWeek);

    LocalDateTime start = weekStart.atStartOfDay();
    LocalDateTime end =
        isCurrent ? LocalDateTime.now() : weekStart.plusDays(6).atTime(LocalTime.MAX);

    List<MetricDetailItem> items =
        switch (metric.toUpperCase()) {
          case "NEW_USERS" -> buildNewUsersDetail(start, end);
          case "ACTIVE_USERS" -> buildActiveUsersDetail(start, end);
          case "ISSUES_REPORTED" -> buildIssuesReportedDetail(start, end);
          case "ISSUES_VERIFIED" -> buildIssuesVerifiedDetail(start, end);
          case "QUIZ_USERS" -> buildQuizUsersDetail(start, end);
          default -> List.of();
        };

    return MetricDetailResponse.builder().metric(metric).items(items).build();
  }

  public OpsSummaryResponse getOpsSummary() {
    long pendingIssueActions = issueActionRepository.countPendingActions();
    long pendingEvents =
        eventApprovalRepository.countByStatus(EventApprovalStatus.PENDING)
            + eventRepository.countWithoutApprovalRow();
    long feedQueue = feedPostRepository.countModerationQueue();

    return OpsSummaryResponse.builder()
        .pendingIssueActions(pendingIssueActions)
        .pendingEvents(pendingEvents)
        .feedModerationQueue(feedQueue)
        .build();
  }

  // ---- Detail builders ----

  private List<MetricDetailItem> buildNewUsersDetail(LocalDateTime start, LocalDateTime end) {
    return userRepository.findNewUsersBetween(start, end).stream()
        .map(
            u -> {
              String platform =
                  userAuthSessionRepository
                      .findFirstByUser_IdOrderByCreatedAtAsc(u.getId())
                      .map(s -> s.getPlatform() != null ? s.getPlatform().name() : null)
                      .orElse(null);
              return MetricDetailItem.builder()
                  .username(u.getUsername())
                  .locality(localityName(u))
                  .platform(platform)
                  .date(
                      u.getCreatedAt() != null
                          ? u.getCreatedAt().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)
                          : null)
                  .build();
            })
        .toList();
  }

  private List<MetricDetailItem> buildActiveUsersDetail(LocalDateTime start, LocalDateTime end) {
    return userRepository.findActiveUsersBetween(start, end).stream()
        .map(
            u ->
                MetricDetailItem.builder()
                    .username(u.getUsername())
                    .locality(localityName(u))
                    .build())
        .toList();
  }

  private List<MetricDetailItem> buildIssuesReportedDetail(LocalDateTime start, LocalDateTime end) {
    return issueRepository.findReportedBetween(start, end).stream()
        .map(
            i ->
                MetricDetailItem.builder()
                    .issueKey(i.getKey())
                    .username(i.getUserEntity() != null ? i.getUserEntity().getUsername() : null)
                    .locality(issueLocality(i))
                    .date(
                        i.getCreatedAt() != null
                            ? i.getCreatedAt().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)
                            : null)
                    .build())
        .toList();
  }

  private List<MetricDetailItem> buildIssuesVerifiedDetail(LocalDateTime start, LocalDateTime end) {
    return issueActionRepository.findVerifiedActionsBetween(start, end).stream()
        .map(
            ia -> {
              IssueEntity issue = ia.getIssueEntity();
              return MetricDetailItem.builder()
                  .issueKey(issue != null ? issue.getKey() : null)
                  .locality(issue != null ? issueLocality(issue) : null)
                  .date(
                      ia.getApprovedAt() != null
                          ? ia.getApprovedAt().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)
                          : null)
                  .build();
            })
        .toList();
  }

  private List<MetricDetailItem> buildQuizUsersDetail(LocalDateTime start, LocalDateTime end) {
    List<Long> userIds = quizAttemptRepository.findDistinctUserIdsBetween(start, end);
    return userRepository.findAllById(userIds).stream()
        .map(
            u ->
                MetricDetailItem.builder()
                    .username(u.getUsername())
                    .locality(localityName(u))
                    .build())
        .toList();
  }

  // ---- Helpers ----

  private static LocalDate isoWeekStart(int weekYear, int weekNumber) {
    return LocalDate.of(weekYear, 1, 4)
        .with(IsoFields.WEEK_BASED_YEAR, weekYear)
        .with(IsoFields.WEEK_OF_WEEK_BASED_YEAR, weekNumber)
        .with(DayOfWeek.MONDAY);
  }

  private static String localityName(UserEntity user) {
    Location loc = user.getLocation();
    if (loc == null) return null;
    Locality locality = loc.getLocality();
    return locality != null ? locality.getName() : null;
  }

  private static String issueLocality(IssueEntity issue) {
    Location loc = issue.getLocation();
    if (loc == null) return null;
    Locality locality = loc.getLocality();
    return locality != null ? locality.getName() : null;
  }
}
