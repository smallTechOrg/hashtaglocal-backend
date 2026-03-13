package org.smalltech.hashtaglocal_backend.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Value;
import lombok.extern.jackson.Jacksonized;

@Value
@Builder
@Jacksonized
public class TrackIssueScrapeRequestDTO {

  String source;
  Context context;

  public static TrackIssueScrapeRequestDTO of(
      String portal, String trackingId, String username, String password) {
    return TrackIssueScrapeRequestDTO.builder()
        .source("GOV_ISSUE_PORTAL")
        .context(
            Context.builder()
                .portal(portal)
                .action(
                    Action.builder()
                        .type("TRACK_ISSUE")
                        .data(ActionData.builder().trackingId(trackingId).build())
                        .build())
                .auth(Auth.builder().username(username).password(password).build())
                .build())
        .build();
  }

  @Value
  @Builder
  @Jacksonized
  public static class Context {
    String portal;
    Action action;
    Auth auth;
  }

  @Value
  @Builder
  @Jacksonized
  public static class Action {
    String type;
    ActionData data;
  }

  @Value
  @Builder
  @Jacksonized
  public static class ActionData {
    @JsonProperty("tracking_id")
    String trackingId;
  }

  @Value
  @Builder
  @Jacksonized
  public static class Auth {
    String username;
    String password;
  }
}
