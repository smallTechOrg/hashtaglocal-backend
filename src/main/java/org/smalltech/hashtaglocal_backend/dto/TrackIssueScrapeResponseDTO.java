package org.smalltech.hashtaglocal_backend.dto;

import java.util.Map;
import lombok.Builder;
import lombok.Value;
import lombok.extern.jackson.Jacksonized;

@Value
@Builder
@Jacksonized
public class TrackIssueScrapeResponseDTO {

  Data data;

  @Value
  @Builder
  @Jacksonized
  public static class Data {
    String status;
    Map<String, Object> metaData;
  }
}
