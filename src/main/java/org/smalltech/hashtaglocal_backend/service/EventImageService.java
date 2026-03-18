package org.smalltech.hashtaglocal_backend.service;

import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.smalltech.hashtaglocal_backend.entity.MediaEntity;
import org.smalltech.hashtaglocal_backend.model.MediaTypeModel;
import org.smalltech.hashtaglocal_backend.repository.MediaRepository;
import org.smalltech.hashtaglocal_backend.util.FileNamingPolicy;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

/**
 * Downloads an event banner image from a CDN URL and persists it in GCS.
 *
 * <p>The image bytes are held entirely in memory (CDN → byte[] → GCS). No temp file is written to
 * disk, so there is nothing to clean up after the upload.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class EventImageService {

  private final GCSService gcsService;
  private final RestTemplate restTemplate;
  private final MediaRepository mediaRepository;
  private final FileNamingPolicy fileNamingPolicy;

  private static final String GCS_PREFIX = "events/";
  private static final List<String> KNOWN_IMAGE_EXTENSIONS =
      List.of("jpg", "jpeg", "png", "webp", "gif");

  /**
   * Downloads the image at {@code imageUrl} directly into memory, uploads it to GCS under {@code
   * events/<uuid>.<ext>}, saves a {@link MediaEntity} row, and returns it.
   *
   * @return the saved {@link MediaEntity}, or {@code null} if download or upload fails
   */
  public MediaEntity downloadAndStore(String imageUrl) {
    try {
      byte[] bytes = restTemplate.getForObject(imageUrl, byte[].class);
      if (bytes == null || bytes.length == 0) {
        log.warn("Image download returned empty bytes for '{}'", imageUrl);
        return null;
      }

      String ext = detectExtension(imageUrl);
      String contentType = toContentType(ext);
      String objectName = GCS_PREFIX + fileNamingPolicy.generate(ext);
      String gcsPath = gcsService.uploadObject(objectName, bytes, contentType);
      log.debug("Uploaded event image to {}", gcsPath);

      try {
        MediaEntity media = MediaEntity.builder().type(MediaTypeModel.PHOTO).url(gcsPath).build();
        return mediaRepository.save(media);
      } catch (Exception dbEx) {
        log.warn(
            "DB save failed for media, deleting orphaned GCS object {}: {}",
            gcsPath,
            dbEx.getMessage());
        gcsService.deleteObject(gcsPath);
        return null;
      }
    } catch (Exception e) {
      log.warn("Failed to download/store image from '{}': {}", imageUrl, e.getMessage());
      return null;
    }
  }

  private String detectExtension(String url) {
    String path = url.split("\\?")[0]; // strip query params
    int dot = path.lastIndexOf('.');
    if (dot > 0 && dot < path.length() - 1) {
      String ext = path.substring(dot + 1).toLowerCase();
      if (KNOWN_IMAGE_EXTENSIONS.contains(ext)) return ext;
    }
    return "jpg";
  }

  private String toContentType(String ext) {
    return switch (ext) {
      case "png" -> "image/png";
      case "webp" -> "image/webp";
      case "gif" -> "image/gif";
      default -> "image/jpeg"; // jpg, jpeg, fallback
    };
  }
}
