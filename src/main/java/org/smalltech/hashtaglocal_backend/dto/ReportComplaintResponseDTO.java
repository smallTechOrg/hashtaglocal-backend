package org.smalltech.hashtaglocal_backend.dto;

import lombok.Builder;
import lombok.Value;
import lombok.extern.jackson.Jacksonized;

@Value
@Builder
@Jacksonized
public class ReportComplaintResponseDTO {
  Long trackingId;
}
