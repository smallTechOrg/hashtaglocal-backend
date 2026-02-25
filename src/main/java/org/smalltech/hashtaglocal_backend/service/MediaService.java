package org.smalltech.hashtaglocal_backend.service;

import org.smalltech.hashtaglocal_backend.model.SignedUrlResponse;

// Service interface for media-related operations, currently focused on generating signed URLs for
// uploads
public interface MediaService {
  SignedUrlResponse generateUploadUrl(String contentType);
}
