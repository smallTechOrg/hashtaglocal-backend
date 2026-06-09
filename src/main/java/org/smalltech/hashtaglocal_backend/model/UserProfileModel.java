package org.smalltech.hashtaglocal_backend.model;

import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserProfileModel {

  private String username;
  private String picture;
  private String userRole;
  private String hashtag;
  private List<String> hashtags;
  private UserSummaryModel userSummary;
}
