package org.smalltech.hashtaglocal_backend.model.response;

import lombok.Builder;
import lombok.Data;

/** A locality option for ops-portal dropdowns (the saved-user localities the jobs run over). */
@Data
@Builder
public class AdminLocalityOptionData {

  private Long id;
  private String name;
  private String hashtag;
}
