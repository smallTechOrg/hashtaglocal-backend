package org.smalltech.hashtaglocal_backend.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.LocalDateTime;
import lombok.Data;

/** Request body for {@code PUT /admin/event/{eventId}}. All fields are optional (null = no change). */
@Data
public class AdminEditEventRequest {

  private String name;

  private String organisation;

  private String address;

  @JsonProperty("start_time")
  private LocalDateTime startTime;

  @JsonProperty("end_time")
  private LocalDateTime endTime;

  private String link;

  /**
   * One of the {@link org.smalltech.hashtaglocal_backend.model.EventTypeModel} enum names. Ignored
   * if unrecognised.
   */
  private String type;

  /** Override displayed name shown to app users. Pass blank string to clear an existing override. */
  @JsonProperty("display_name")
  private String displayName;
}
