package org.smalltech.hashtaglocal_backend.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** DTO for raw discovery from a single source. */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RawDiscoveryDTO {

  private String name;

  private String state;

  private String countryCode;

  private String localityType;

  private String source;

  private String sourceMetadata;
}
