package org.smalltech.hashtaglocal_backend.dto;

import java.time.LocalDateTime;
import lombok.Builder;
import lombok.Value;
import lombok.extern.jackson.Jacksonized;

/**
 * Represents a single event entry in the scrape service response.
 *
 * <p>Field names use camelCase; Jackson's global SNAKE_CASE naming strategy maps them from the wire
 * JSON (e.g. {@code start_time} → {@code startTime}).
 */
@Value
@Builder
@Jacksonized
public class ScrapeEventDTO {
  String name;
  String image;
  String portal;
  LocalDateTime startTime;
  LocalDateTime endTime;
  String address;
  String link;
  String organisation;
  String type;
}
