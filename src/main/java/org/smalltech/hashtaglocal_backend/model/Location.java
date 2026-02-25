package org.smalltech.hashtaglocal_backend.model;

import lombok.*;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder(toBuilder = true)
public class Location {
  private Double lat;
  private Double lng;
  private Locality locality;
  private String address;
  private String colloquialName;
}
