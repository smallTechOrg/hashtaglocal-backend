package org.smalltech.hashtaglocal_backend.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class IssueCountModel {

  private long total;
  private long onhold;
  private long open;
  private long resolved;
  private long verify;
  private long resolvedOthers;
}
