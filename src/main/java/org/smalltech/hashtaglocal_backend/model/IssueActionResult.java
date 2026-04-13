package org.smalltech.hashtaglocal_backend.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;

@Data
@AllArgsConstructor
@Builder
public class IssueActionResult {
  private Long issueId;
  private int karmaAwarded;
}
