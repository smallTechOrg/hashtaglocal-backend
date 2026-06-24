package org.smalltech.hashtaglocal_backend.model.response;

import java.time.LocalDate;
import java.util.List;

public record WeekCoverageData(
    LocalDate from,
    LocalDate to,
    long totalExpected,
    long totalReady,
    long totalMissing,
    List<LocalityCoverage> localities) {

  public record LocalityCoverage(
      Long id, String name, String hashtag, List<DateSlot> dates, long missingCount) {}

  public record DateSlot(LocalDate date, boolean hasQuiz) {}
}
