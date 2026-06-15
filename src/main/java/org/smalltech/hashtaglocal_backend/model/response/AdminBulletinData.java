package org.smalltech.hashtaglocal_backend.model.response;

import java.time.LocalDate;
import java.util.Map;
import lombok.Builder;
import lombok.Data;

/** A bulletin row in the ops portal: weather payload + editable summary + quiz link. */
@Data
@Builder
public class AdminBulletinData {

  private Long id;
  private Long localityId;
  private String localityName;
  private String hashtag;
  private LocalDate date;

  private Map<String, Object> weather;
  private String weatherSource;
  private String summary;

  private Long quizId;
}
