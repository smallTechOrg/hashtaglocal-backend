package org.smalltech.hashtaglocal_backend.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Value;
import lombok.extern.jackson.Jacksonized;
import org.smalltech.hashtaglocal_backend.model.EventPortalModel;
import org.smalltech.hashtaglocal_backend.model.ScrapeActionType;
import org.smalltech.hashtaglocal_backend.model.ScrapeSource;

@Value
@Builder
@Jacksonized
public class FetchEventsScrapeRequestDTO {

  String source;
  Context context;

  public static FetchEventsScrapeRequestDTO of(EventPortalModel portal) {
    return FetchEventsScrapeRequestDTO.builder()
        .source(ScrapeSource.EVENT_PORTAL.name())
        .context(
            Context.builder()
                .portal(portal.name())
                .action(
                    Action.builder()
                        .type(ScrapeActionType.FETCH_EVENTS.name())
                        .data(
                            ActionData.builder()
                                .eventFilter(portal.getEventFilter())
                                .categoryFilter(portal.getCategoryFilter())
                                .build())
                        .build())
                .build())
        .build();
  }

  @Value
  @Builder
  @Jacksonized
  public static class Context {
    String portal;
    Action action;
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
    @JsonProperty("event_filter")
    String eventFilter;

    @JsonProperty("category_filter")
    String categoryFilter;
  }
}
