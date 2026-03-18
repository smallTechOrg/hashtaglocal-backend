package org.smalltech.hashtaglocal_backend.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.Map;
import lombok.Builder;
import lombok.Value;
import lombok.extern.jackson.Jacksonized;
import org.smalltech.hashtaglocal_backend.model.ScrapeActionType;
import org.smalltech.hashtaglocal_backend.model.ScrapeSource;

@Value
@Builder
@Jacksonized
public class ScrapeRequestDTO {

  String source;
  Context context;

  /** Builds a request without auth (e.g. FETCH_EVENTS). */
  public static ScrapeRequestDTO of(
      ScrapeSource source, ScrapeActionType actionType, String portal, Map<String, Object> data) {
    return of(source, actionType, portal, data, null);
  }

  /** Builds a request with auth (e.g. TRACK_ISSUE). */
  public static ScrapeRequestDTO of(
      ScrapeSource source,
      ScrapeActionType actionType,
      String portal,
      Map<String, Object> data,
      Auth auth) {
    return ScrapeRequestDTO.builder()
        .source(source.name())
        .context(
            Context.builder()
                .portal(portal)
                .action(Action.builder().type(actionType.name()).data(data).build())
                .auth(auth)
                .build())
        .build();
  }

  @Value
  @Builder
  @Jacksonized
  @JsonInclude(JsonInclude.Include.NON_NULL)
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
    Map<String, Object> data;
  }

  @Value
  @Builder
  @Jacksonized
  public static class Auth {
    String username;
    String password;
  }
}
