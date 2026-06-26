package org.smalltech.hashtaglocal_backend.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.LocalDate;
import java.time.temporal.IsoFields;
import lombok.RequiredArgsConstructor;
import org.smalltech.hashtaglocal_backend.dto.MetricDetailResponse;
import org.smalltech.hashtaglocal_backend.dto.OpsSummaryResponse;
import org.smalltech.hashtaglocal_backend.dto.WeeklyMetricsResponse;
import org.smalltech.hashtaglocal_backend.model.NewAPIResponse;
import org.smalltech.hashtaglocal_backend.service.MetricsService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/admin/metrics")
@Tag(name = "Admin — Metrics", description = "Key metrics dashboard and ops action summary.")
@SecurityRequirement(name = "bearerAuth")
@RequiredArgsConstructor
public class MetricsAdminController {

  private final MetricsService metricsService;

  @GetMapping
  @Operation(
      summary = "Weekly metrics",
      description =
          "Returns computed metrics for each ISO week in the specified range. "
              + "Defaults to the last 8 weeks ending at the current week.")
  public ResponseEntity<NewAPIResponse<WeeklyMetricsResponse>> getWeeklyMetrics(
      @RequestParam(required = false) Integer fromWeekYear,
      @RequestParam(required = false) Integer fromWeekNumber,
      @RequestParam(required = false) Integer toWeekYear,
      @RequestParam(required = false) Integer toWeekNumber) {

    LocalDate today = LocalDate.now();
    int curYear = today.get(IsoFields.WEEK_BASED_YEAR);
    int curWeek = today.get(IsoFields.WEEK_OF_WEEK_BASED_YEAR);

    if (toWeekYear == null) toWeekYear = curYear;
    if (toWeekNumber == null) toWeekNumber = curWeek;
    if (fromWeekYear == null || fromWeekNumber == null) {
      // Default: 8 weeks back from toWeek
      LocalDate toStart = isoWeekStart(toWeekYear, toWeekNumber);
      LocalDate fromStart = toStart.minusWeeks(7);
      fromWeekYear = fromStart.get(IsoFields.WEEK_BASED_YEAR);
      fromWeekNumber = fromStart.get(IsoFields.WEEK_OF_WEEK_BASED_YEAR);
    }

    WeeklyMetricsResponse response =
        metricsService.getWeeklyMetrics(fromWeekYear, fromWeekNumber, toWeekYear, toWeekNumber);
    return ResponseEntity.ok(
        NewAPIResponse.<WeeklyMetricsResponse>builder().data(response).build());
  }

  @GetMapping("/detail")
  @Operation(
      summary = "Metric drill-down",
      description =
          "Returns the individual users/issues behind a computed metric for a specific week. "
              + "Supported metrics: NEW_USERS, ACTIVE_USERS, ISSUES_REPORTED, ISSUES_VERIFIED, QUIZ_USERS.")
  public ResponseEntity<NewAPIResponse<MetricDetailResponse>> getMetricDetail(
      @RequestParam String metric, @RequestParam int weekYear, @RequestParam int weekNumber) {

    MetricDetailResponse response = metricsService.getMetricDetail(metric, weekYear, weekNumber);
    return ResponseEntity.ok(NewAPIResponse.<MetricDetailResponse>builder().data(response).build());
  }

  @GetMapping("/ops-summary")
  @Operation(
      summary = "Ops action summary",
      description =
          "Returns counts of items awaiting admin action: pending issue actions, pending events, and feed moderation queue.")
  public ResponseEntity<NewAPIResponse<OpsSummaryResponse>> getOpsSummary() {
    OpsSummaryResponse response = metricsService.getOpsSummary();
    return ResponseEntity.ok(NewAPIResponse.<OpsSummaryResponse>builder().data(response).build());
  }

  private static LocalDate isoWeekStart(int weekYear, int weekNumber) {
    return LocalDate.of(weekYear, 1, 4)
        .with(IsoFields.WEEK_BASED_YEAR, weekYear)
        .with(IsoFields.WEEK_OF_WEEK_BASED_YEAR, weekNumber)
        .with(java.time.DayOfWeek.MONDAY);
  }
}
