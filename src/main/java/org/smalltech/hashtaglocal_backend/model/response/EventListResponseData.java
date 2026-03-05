package org.smalltech.hashtaglocal_backend.model.response;

import java.util.List;
import lombok.Builder;
import lombok.Data;

/**
 * Top-level response wrapper for the event list endpoint ({@code GET /api/v1/events}).
 *
 * <p>Wrapped inside {@link org.smalltech.hashtaglocal_backend.model.NewAPIResponse} so the JSON
 * response shape is: {@code { "data": { "events": [...] } }}.
 */
@Data
@Builder
public class EventListResponseData {

  /** All events returned by the query. */
  private List<EventData> events;
}
