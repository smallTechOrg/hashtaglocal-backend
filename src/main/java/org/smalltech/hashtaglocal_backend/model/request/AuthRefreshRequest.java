package org.smalltech.hashtaglocal_backend.model.request;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AuthRefreshRequest {
  @NotBlank(message = "refresh_token is required")
  @JsonProperty("refresh_token")
  private String refreshToken;

  @JsonProperty("notification_token")
  private String notificationToken;

  @JsonProperty("device_id")
  private String deviceId;
}
