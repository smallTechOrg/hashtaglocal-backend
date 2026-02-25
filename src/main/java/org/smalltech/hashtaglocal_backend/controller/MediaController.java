package org.smalltech.hashtaglocal_backend.controller;

import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.smalltech.hashtaglocal_backend.model.NewAPIResponse;
import org.smalltech.hashtaglocal_backend.model.response.MediaUploadResponseData;
import org.smalltech.hashtaglocal_backend.service.MediaService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

// Generates signed URLs for media uploads to Google Cloud Storage
@RestController
@RequestMapping("/api/v1/media")
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "Media", description = "Media upload and signed URL APIs")
public class MediaController {

  private final MediaService mediaService;

  public MediaController(MediaService mediaService) {
    this.mediaService = mediaService;
  }

  @GetMapping("/upload-url")
  public NewAPIResponse<MediaUploadResponseData> getSignedUrl(
      @RequestParam("content_type") String contentType) {
    var mediaUrl = mediaService.generateUploadUrl(contentType);
    return NewAPIResponse.<MediaUploadResponseData>builder()
        .data(MediaUploadResponseData.builder().mediaUrl(mediaUrl).build())
        .build();
  }
}
