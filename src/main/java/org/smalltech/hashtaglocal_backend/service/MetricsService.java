package org.smalltech.hashtaglocal_backend.service;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.IsoFields;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.function.BinaryOperator;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.smalltech.hashtaglocal_backend.dto.MetricDetailItem;
import org.smalltech.hashtaglocal_backend.dto.MetricDetailResponse;
import org.smalltech.hashtaglocal_backend.dto.OpsSummaryResponse;
import org.smalltech.hashtaglocal_backend.dto.WeeklyMetricPoint;
import org.smalltech.hashtaglocal_backend.dto.WeeklyMetricsResponse;
import org.smalltech.hashtaglocal_backend.entity.AppVersionSeenEntity;
import org.smalltech.hashtaglocal_backend.entity.IssueEntity;
import org.smalltech.hashtaglocal_backend.entity.Locality;
import org.smalltech.hashtaglocal_backend.entity.Location;
import org.smalltech.hashtaglocal_backend.entity.UserEntity;
import org.smalltech.hashtaglocal_backend.model.EventApprovalStatus;
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
import org.smalltech.hashtaglocal_backend.util.AppVersionUtil;
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
  private final AppVersionSeenRepository appVersionSeenRepository;

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
      AppVersionWeek versions = appVersionWeek(year, week);

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
              .latestAppVersion(versions.latest())
              .onLatestVersionPct(versions.percentOf(versions.onLatestCount()))
              .outdatedUsers(versions.hasUsers() ? (long) versions.outdated().size() : null)
              .outdatedPct(versions.percentOf(versions.outdated().size()))
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
          case "OUTDATED_USERS" -> buildOutdatedUsersDetail(weekYear, weekNumber);
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

  private List<MetricDetailItem> buildOutdatedUsersDetail(int weekYear, int weekNumber) {
    List<AppVersionSeenEntity> outdated = appVersionWeek(weekYear, weekNumber).outdated();
    Map<Long, UserEntity> users =
        userRepository
            .findAllById(outdated.stream().map(AppVersionSeenEntity::getUserId).toList())
            .stream()
            .collect(Collectors.toMap(UserEntity::getId, Function.identity()));
    return outdated.stream()
        .sorted(Comparator.comparing(AppVersionSeenEntity::getLastSeenAt).reversed())
        .map(
            seen -> {
              UserEntity u = users.get(seen.getUserId());
              return MetricDetailItem.builder()
                  .username(u != null ? u.getUsername() : null)
                  .locality(u != null ? localityName(u) : null)
                  .platform(seen.getPlatform() != null ? seen.getPlatform().name() : null)
                  .appVersion(seen.getAppVersion())
                  .date(seen.getLastSeenAt().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME))
                  .build();
            })
        .toList();
  }

  // ---- App version metrics ----

  /** A user counts as outdated when their version is this many releases behind the latest. */
  static final int OUTDATED_RELEASES_BEHIND = 2;

  /**
   * The app versions of one ISO week.
   *
   * @param latest the newest version reported in this week or any earlier one; null before any
   * @param perUser each user whose app reported a version this week, with their newest one
   * @param onLatestCount how many of those users are on {@code latest}
   * @param outdated those users who are {@link #OUTDATED_RELEASES_BEHIND} or more releases behind
   */
  record AppVersionWeek(
      String latest,
      List<AppVersionSeenEntity> perUser,
      long onLatestCount,
      List<AppVersionSeenEntity> outdated) {

    boolean hasUsers() {
      return !perUser.isEmpty();
    }

    /** Whole-number share of this week's users, or null when no user reported a version. */
    Integer percentOf(long count) {
      return hasUsers() ? (int) Math.round(count * 100.0 / perUser.size()) : null;
    }
  }

  /**
   * Releases are the distinct versions reported up to the end of the week, in version order, so
   * "releases behind" is the distance between two versions in that list. A user with two devices
   * counts once, on the newer version.
   */
  AppVersionWeek appVersionWeek(int weekYear, int weekNumber) {
    Comparator<String> byVersion = AppVersionUtil::compare;
    List<String> releases =
        appVersionSeenRepository.findVersionsSeenUpTo(weekYear, weekNumber).stream()
            .sorted(byVersion)
            .toList();
    if (releases.isEmpty()) {
      return new AppVersionWeek(null, List.of(), 0, List.of());
    }
    String latest = releases.get(releases.size() - 1);
    int latestRank = releases.size() - 1;

    List<AppVersionSeenEntity> perUser =
        appVersionSeenRepository.findByWeekYearAndWeekNumber(weekYear, weekNumber).stream()
            .collect(
                Collectors.toMap(
                    AppVersionSeenEntity::getUserId,
                    Function.identity(),
                    BinaryOperator.maxBy(
                        Comparator.comparing(AppVersionSeenEntity::getAppVersion, byVersion))))
            .values()
            .stream()
            .toList();

    long onLatest = perUser.stream().filter(s -> s.getAppVersion().equals(latest)).count();
    List<AppVersionSeenEntity> outdated =
        perUser.stream()
            .filter(
                s -> latestRank - releases.indexOf(s.getAppVersion()) >= OUTDATED_RELEASES_BEHIND)
            .toList();
    return new AppVersionWeek(latest, perUser, onLatest, outdated);
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
