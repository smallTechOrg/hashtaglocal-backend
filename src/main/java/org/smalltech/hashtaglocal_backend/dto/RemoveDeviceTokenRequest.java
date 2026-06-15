package org.smalltech.hashtaglocal_backend.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.smalltech.hashtaglocal_backend.model.Platform;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RemoveDeviceTokenRequest {
  private Platform platform;
}
