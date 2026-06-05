package org.smalltech.hashtaglocal_backend.model.request;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.smalltech.hashtaglocal_backend.model.Platform;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OAuthRequest {

  private String code;
  private String codeVerifier;
  private String redirectUri;
  private String accessToken;
  private String identityToken;
  private String fullName;
  private String notificationToken;
  private Platform platform;
}
