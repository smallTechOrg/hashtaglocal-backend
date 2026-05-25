package org.smalltech.hashtaglocal_backend.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.LocalDateTime;
import lombok.Data;

/** Request body for {@code POST /admin/event/manual}. */
@Data
public class AdminCreateEventRequest {

  private String name;

  private String organisation;

  private String address;

  @JsonProperty("start_time")
  private LocalDateTime startTime;

  @JsonProperty("end_time")
  private LocalDateTime endTime;

  private String link;

  /**
   * One of the {@link org.smalltech.hashtaglocal_backend.model.EventTypeModel} enum names. Defaults
   * to OTHER if omitted or unrecognised.
   */
  private String type;
}
