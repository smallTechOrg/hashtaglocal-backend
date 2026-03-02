package org.smalltech.hashtaglocal_backend.dto;

import java.util.List;
import lombok.Builder;
import lombok.Value;
import lombok.extern.jackson.Jacksonized;

/**
 * Top-level wrapper for the scrape service response.
 *
 * <p>Expected JSON shape:
 *
 * <pre>
 * {
 *   "data": {
 *     "events": [ ... ]
 *   }
 * }
 * </pre>
 */
@Value
@Builder
@Jacksonized
public class ScrapeResponseDTO {

  Data data;

  @Value
  @Builder
  @Jacksonized
  public static class Data {
    List<ScrapeEventDTO> events;
  }
}
