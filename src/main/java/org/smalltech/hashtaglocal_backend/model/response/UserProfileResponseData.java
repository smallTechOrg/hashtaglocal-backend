package org.smalltech.hashtaglocal_backend.model.response;

import lombok.Builder;
import lombok.Data;
import org.smalltech.hashtaglocal_backend.model.UserProfileModel;

@Data
@Builder
public class UserProfileResponseData {
  private UserProfileModel user;
}
