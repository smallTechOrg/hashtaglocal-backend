package org.smalltech.hashtaglocal_backend.dto;

import lombok.Data;

/** Request body for approving an event. All fields are optional. */
@Data
public class EventApproveRequest {

  /**
   * Optional display name override. When non-blank, this replaces the scraped event name shown on
   * the public website without modifying the original {@code events} table row.
   */
  private String displayName;
}
