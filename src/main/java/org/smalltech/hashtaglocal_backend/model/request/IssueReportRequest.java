package org.smalltech.hashtaglocal_backend.model.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class IssueReportRequest {

  @Valid
  @NotNull(message = "issue is required")
  private IssueRequest issue;
}
