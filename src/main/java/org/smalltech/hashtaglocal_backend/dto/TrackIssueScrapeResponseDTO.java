package org.smalltech.hashtaglocal_backend.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
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

    @JsonProperty("meta_data")
    Map<String, Object> metaData;
  }
}
