package org.smalltech.hashtaglocal_backend.model;

import java.time.LocalDateTime;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Issue {
  private Long id;
  private User user;
  private Location location;
  private String type;
  private String description;
  private LocalDateTime createdAt;
  private List<Media> mediaUrls;
  private List<GovPortalData> govPortalData;
  private int voteCount;
  private int verifyCount;
  private String status;
  private int rank;
  private ViewerContext viewerContext;
}
