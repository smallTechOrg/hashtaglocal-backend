package org.smalltech.hashtaglocal_backend.model.response;

import lombok.Builder;
import lombok.Data;

/** How many quizzes are queued for one saved-user locality (0 when none). */
@Data
@Builder
public class QueuedQuizCountData {

  private Long localityId;
  private String localityName;
  private String hashtag;
  private long queued;
}
