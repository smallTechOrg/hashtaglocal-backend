package org.smalltech.hashtaglocal_backend.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@JsonInclude(JsonInclude.Include.NON_NULL)
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Media {
  private Location location;
  private String type;
  private String url;
  private String urlThumbnail;
  private String description;
  private String username;
  private LocalDateTime createdAt;
}
