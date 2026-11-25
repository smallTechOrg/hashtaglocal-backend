package org.smalltech.hashtaglocal_backend.dto;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
@JsonNaming(PropertyNamingStrategies.LowerCamelCaseStrategy.class)
public class WeeklyMetricPoint {

  private String weekLabel;
  private int weekYear;
  private int weekNumber;
  private String startDate;
  private String endDate;
  private boolean isCurrentWeek;

  // User metrics
  private long newUsers;
  private long activeUsers;
  private long notificationsSent;

  // Engagement metrics
  private long issuesReported;
  private long issuesVerified;
  private double dailyAvgQuiz;
  private long uniqueQuizUsers;

  // App version metrics: users whose app reported a version this week (login or token refresh).
  // Null when there is no data for the week.
  private String latestAppVersion;
  private Integer onLatestVersionPct;
  private Long outdatedUsers;
  private Integer outdatedPct;
}
