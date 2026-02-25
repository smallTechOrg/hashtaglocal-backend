package org.smalltech.hashtaglocal_backend.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;
import lombok.*;

@JsonInclude(JsonInclude.Include.NON_NULL)
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ResponseData {
  private Issue issue;
  private List<Issue> issues;
  private SignedUrlResponse mediaUrl;
  private Long issueId;
  private TokenResponse refreshToken;
  private TokenResponse accessToken;
  private UserProfileModel user;
}
