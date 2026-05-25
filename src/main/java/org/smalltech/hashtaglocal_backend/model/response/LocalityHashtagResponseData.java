package org.smalltech.hashtaglocal_backend.model.response;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class LocalityHashtagResponseData {
  private String hashtag;
}
