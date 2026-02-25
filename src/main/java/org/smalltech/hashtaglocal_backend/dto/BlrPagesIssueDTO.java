package org.smalltech.hashtaglocal_backend.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BlrPagesIssueDTO {
  private String uuid;
  private Double lat;

  @JsonProperty("long")
  private Double lng;

  private String image;

  @JsonProperty("image_thumb")
  private String imageThumb;

  private Integer category;

  @JsonProperty("created_at")
  private String createdAt;
}
