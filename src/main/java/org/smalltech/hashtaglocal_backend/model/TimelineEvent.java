package org.smalltech.hashtaglocal_backend.model;

import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TimelineEvent {

  private String event;
  private LocalDateTime timestamp;
  private String details;
}
