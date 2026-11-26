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

  /** Users whose app has reported a version. */
  private long totalUsers;

  /** One entry per version, newest first. */
  private List<VersionCount> versions;

  /** One entry per user, most recently active first. */
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

    /** ISO date-time the user's app last talked to the server. */
    private String lastActive;
  }
}
