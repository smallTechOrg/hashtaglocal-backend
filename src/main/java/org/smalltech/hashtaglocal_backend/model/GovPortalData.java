package org.smalltech.hashtaglocal_backend.model;

import java.time.LocalDateTime;
import java.util.Map;
import lombok.*;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class GovPortalData {

  private String trackingId;
  private String status;
  private String portalName;
  private String portalTrackLink;
  private Map<String, Object> metaData;
  private LocalDateTime createdAt;
  private LocalDateTime updatedAt;
}
