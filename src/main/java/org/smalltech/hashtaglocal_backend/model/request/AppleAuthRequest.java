package org.smalltech.hashtaglocal_backend.model.request;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.smalltech.hashtaglocal_backend.model.Platform;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AppleAuthRequest {

  @NotBlank(message = "identity_token is required")
  private String identityToken;

  /** Full name from the Apple credential. Only provided on first sign-in; may be null. */
  private String fullName;

  @JsonProperty("notification_token")
  private String notificationToken;

  private Platform platform;

  @JsonProperty("device_id")
  private String deviceId;
}
