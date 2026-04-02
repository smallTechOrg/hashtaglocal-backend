package org.smalltech.hashtaglocal_backend.model;

public final class KarmaPointsConfig {

  private KarmaPointsConfig() {}

  public static final int REPORT = 5;
  public static final int VERIFY = 3;
  public static final int REPORTED_ISSUE_VERIFIED = 1;
  public static final int RESOLVE = 5;
  public static final int DAILY_LOGIN = 1;

  public static int pointsFor(KarmaTransactionType type) {
    return switch (type) {
      case REPORT -> REPORT;
      case VERIFY -> VERIFY;
      case REPORTED_ISSUE_VERIFIED -> REPORTED_ISSUE_VERIFIED;
      case RESOLVE -> RESOLVE;
      case DAILY_LOGIN -> DAILY_LOGIN;
    };
  }
}
