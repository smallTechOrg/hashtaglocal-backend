package org.smalltech.hashtaglocal_backend.model.response;

import lombok.Builder;
import lombok.Data;
import org.smalltech.hashtaglocal_backend.model.SignedUrlResponse;

@Data
@Builder
public class MediaUploadResponseData {
  private SignedUrlResponse mediaUrl;
}
