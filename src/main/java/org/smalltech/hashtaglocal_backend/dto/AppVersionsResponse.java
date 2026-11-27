package org.smalltech.hashtaglocal_backend.dto;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import java.util.List;
import lombok.Builder;
import lombok.Data;

/** Which app version each user is on right now. */
@Data
@Builder
@JsonNaming(PropertyNamingStrategies.LowerCamelCaseStrategy.class)
public class AppVersionsResponse {

  /** Highest version any user is on; null when no app has reported a version. */
  private String latestVersion;

  /** Every user, including those whose app version is unknown. */
  private long totalUsers;

  /**
   * Users whose app never reported a version: apps before 1.0.10 (which don't send one) or users
   * with no session left. Counted in totalUsers but not in versions.
   */
  private long unknownUsers;

  /** Whole-number share of totalUsers with an unknown version. */
  private int unknownPercent;

  /** One entry per reported version, newest first. Percentages are shares of totalUsers. */
  private List<VersionCount> versions;

  /** One entry per user, most recently active first; appVersion is null when unknown. */
  private List<UserVersion> users;

  @Data
  @Builder
  @JsonNaming(PropertyNamingStrategies.LowerCamelCaseStrategy.class)
  public static class VersionCount {
    private String version;
    private long users;

    /** Whole-number share of totalUsers. */
    private int percent;
  }

  @Data
  @Builder
  @JsonNaming(PropertyNamingStrategies.LowerCamelCaseStrategy.class)
  public static class UserVersion {
    private String username;
    private String locality;
    private String platform;
    private String appVersion;

    /** ISO date-time the user's app last talked to the server; null when there is no session. */
    private String lastActive;
  }
}
