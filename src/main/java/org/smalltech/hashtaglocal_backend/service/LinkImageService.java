package org.smalltech.hashtaglocal_backend.service;

import java.net.URI;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.smalltech.hashtaglocal_backend.entity.MediaEntity;
import org.smalltech.hashtaglocal_backend.model.MediaTypeModel;
import org.smalltech.hashtaglocal_backend.repository.MediaRepository;
import org.smalltech.hashtaglocal_backend.util.FileNamingPolicy;
import org.smalltech.hashtaglocal_backend.util.Ssrf;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * Re-hosts a link preview's Open Graph image in GCS so feed link cards are self-contained (source
 * CDN URLs expire — same rationale as {@code EventEntity.media}). SSRF-guarded and size-capped
 * because the image URL comes from arbitrary scraped pages. See FEED_DESIGN.md §6.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class LinkImageService {

  private static final String GCS_PREFIX = "feed-links/";

  private final GCSService gcsService;
  private final MediaRepository mediaRepository;
  private final FileNamingPolicy fileNamingPolicy;

  @Value("${feed.scrape.timeout-ms:8000}")
  private int timeoutMs;

  @Value("${feed.scrape.max-image-bytes:5242880}")
  private int maxImageBytes;

  /**
   * Download {@code imageUrl} into memory (SSRF-checked, size-capped), upload to GCS, and return
   * the saved {@link MediaEntity}. Returns {@code null} on any failure — the card simply renders
   * without a re-hosted image.
   */
  public MediaEntity rehost(String imageUrl) {
    if (imageUrl == null || imageUrl.isBlank()) {
      return null;
    }
    try {
      Ssrf.assertSafeUrl(imageUrl);

      org.jsoup.Connection.Response resp =
          Jsoup.connect(imageUrl)
              .userAgent("HashtagLocalBot/1.0 (+https://local.smalltech.in)")
              .timeout(timeoutMs)
              .maxBodySize(maxImageBytes)
              .ignoreContentType(true)
              .followRedirects(true)
              .execute();

      String contentType = resp.contentType();
      if (contentType == null || !contentType.toLowerCase().startsWith("image/")) {
        log.debug("OG image is not an image ({}): {}", contentType, imageUrl);
        return null;
      }
      byte[] bytes = resp.bodyAsBytes();
      if (bytes.length == 0) {
        return null;
      }

      String ext = extensionFor(contentType, imageUrl);
      String objectName = GCS_PREFIX + fileNamingPolicy.generate(ext);
      String gcsPath = gcsService.uploadObject(objectName, bytes, contentType);

      try {
        return mediaRepository.save(
            MediaEntity.builder().type(MediaTypeModel.PHOTO).url(gcsPath).build());
      } catch (Exception dbEx) {
        gcsService.deleteObject(gcsPath);
        log.warn("DB save failed for re-hosted link image, removed orphan {}", gcsPath);
        return null;
      }
    } catch (Exception e) {
      log.debug("Could not re-host OG image '{}': {}", imageUrl, e.toString());
      return null;
    }
  }

  private static String extensionFor(String contentType, String url) {
    String ct = contentType.toLowerCase();
    if (ct.contains("png")) return "png";
    if (ct.contains("webp")) return "webp";
    if (ct.contains("gif")) return "gif";
    if (ct.contains("svg")) return "svg";
    if (ct.contains("jpeg") || ct.contains("jpg")) return "jpg";
    // Fall back to the URL's extension, else jpg.
    String path = URI.create(url).getPath();
    int dot = path == null ? -1 : path.lastIndexOf('.');
    if (dot > 0 && dot < path.length() - 1) {
      return path.substring(dot + 1).toLowerCase();
    }
    return "jpg";
  }
}
