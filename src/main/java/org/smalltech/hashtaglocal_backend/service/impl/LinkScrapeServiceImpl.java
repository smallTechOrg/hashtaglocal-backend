package org.smalltech.hashtaglocal_backend.service.impl;

import java.net.URI;
import java.util.HashMap;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.smalltech.hashtaglocal_backend.entity.FeedPostContentEntity;
import org.smalltech.hashtaglocal_backend.entity.FeedPostEntity;
import org.smalltech.hashtaglocal_backend.entity.LinkCache;
import org.smalltech.hashtaglocal_backend.entity.MediaEntity;
import org.smalltech.hashtaglocal_backend.model.LinkEmbedType;
import org.smalltech.hashtaglocal_backend.model.LinkScrapeStatus;
import org.smalltech.hashtaglocal_backend.repository.FeedPostRepository;
import org.smalltech.hashtaglocal_backend.repository.LinkCacheRepository;
import org.smalltech.hashtaglocal_backend.service.LinkImageService;
import org.smalltech.hashtaglocal_backend.service.LinkScrapeService;
import org.smalltech.hashtaglocal_backend.util.Ssrf;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Builds a rich, self-contained link card for LINK posts: scrapes Open Graph / Twitter Card
 * metadata (title, description, site name, author, favicon, video), re-hosts the preview image to
 * GCS via {@link LinkImageService} so the card never depends on an expiring source CDN URL, and
 * caches the result by canonical URL. SSRF-guarded: only http/https public hosts, capped
 * size/redirects/timeout. See FEED_DESIGN.md §6.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class LinkScrapeServiceImpl implements LinkScrapeService {

  private static final int MAX_REDIRECTS = 5;

  private final FeedPostRepository feedPostRepository;
  private final LinkCacheRepository linkCacheRepository;
  private final LinkCacheWriter linkCacheWriter;
  private final LinkImageService linkImageService;

  @Value("${feed.scrape.timeout-ms:8000}")
  private int timeoutMs;

  @Value("${feed.scrape.max-body-bytes:2097152}")
  private int maxBodyBytes;

  @Override
  @Transactional
  public void scrape(Long feedPostId) {
    FeedPostEntity post = feedPostRepository.findById(feedPostId).orElse(null);
    if (post == null || post.getContent() == null) {
      return;
    }
    FeedPostContentEntity content = post.getContent();
    if (content.getScrapeStatus() != LinkScrapeStatus.PENDING || content.getUrl() == null) {
      return;
    }

    String canonical = content.getCanonicalUrl();

    // Cache hit → copy and finish.
    if (canonical != null) {
      LinkCache cached = linkCacheRepository.findByCanonicalUrl(canonical).orElse(null);
      if (cached != null) {
        applyCacheToContent(content, cached);
        content.setScrapeStatus(LinkScrapeStatus.OK);
        feedPostRepository.save(post);
        return;
      }
    }

    try {
      Document doc = fetchWithSafeRedirects(content.getUrl());
      String baseUrl =
          doc.baseUri() != null && !doc.baseUri().isBlank() ? doc.baseUri() : content.getUrl();

      // Title: og:title → twitter:title → <title>.
      String title = firstNonBlank(meta(doc, "og:title"), meta(doc, "twitter:title"), doc.title());
      // Description: og:description → twitter:description → <meta name=description>.
      String description =
          firstNonBlank(
              meta(doc, "og:description"),
              meta(doc, "twitter:description"),
              meta(doc, "description"));
      // Image: og:image(:secure_url) → twitter:image → twitter:image:src.
      String imageUrl =
          firstNonBlank(
              meta(doc, "og:image:secure_url"),
              meta(doc, "og:image"),
              meta(doc, "twitter:image"),
              meta(doc, "twitter:image:src"));
      imageUrl = absolutize(imageUrl, baseUrl);
      String siteName = firstNonBlank(meta(doc, "og:site_name"), meta(doc, "application-name"));
      String author =
          firstNonBlank(
              meta(doc, "article:author"), meta(doc, "author"), meta(doc, "twitter:creator"));
      String ogType = meta(doc, "og:type");
      String faviconUrl = absolutize(faviconHref(doc), baseUrl);
      String videoUrl =
          absolutize(firstNonBlank(meta(doc, "og:video:url"), meta(doc, "og:video")), baseUrl);

      // A video card if the page advertises a video; otherwise a standard link card.
      LinkEmbedType embedType =
          (videoUrl != null || (ogType != null && ogType.toLowerCase().contains("video")))
              ? LinkEmbedType.VIDEO
              : LinkEmbedType.LINK;

      content.setTitle(truncate(title, 500));
      if (content.getText() == null || content.getText().isBlank()) {
        content.setText(truncate(description, 4000));
      }
      content.setEmbedType(embedType);

      // Re-host the preview image in GCS so the card is self-contained (source CDN URLs expire).
      MediaEntity image = imageUrl == null ? null : linkImageService.rehost(imageUrl);
      if (image != null) {
        content.setImageMedia(image);
      }

      // Rich, structured tail — everything a modern social-media share card needs.
      Map<String, Object> data =
          content.getData() == null ? new HashMap<>() : new HashMap<>(content.getData());
      putIfPresent(data, "site_name", siteName);
      putIfPresent(data, "author", author);
      putIfPresent(data, "favicon_url", faviconUrl);
      putIfPresent(data, "og_type", ogType);
      putIfPresent(data, "video_url", videoUrl);
      // Keep the original (non-rehosted) image URL as a fallback for the client.
      putIfPresent(data, "image_url", imageUrl);
      content.setData(data);

      content.setScrapeStatus(LinkScrapeStatus.OK);
      feedPostRepository.save(post);

      // Populate the shared cache for reuse, in a separate transaction so a concurrent duplicate
      // insert can't roll back this post's scrape result. Reuse the same re-hosted image.
      linkCacheWriter.saveIfAbsent(
          canonical,
          truncate(title, 500),
          truncate(description, 2000),
          truncate(siteName, 300),
          faviconUrl,
          embedType,
          image);
    } catch (Exception e) {
      log.warn(
          "Link scrape failed for post {} ({}): {}", feedPostId, content.getUrl(), e.toString());
      content.setScrapeStatus(LinkScrapeStatus.FAILED);
      feedPostRepository.save(post);
    }
  }

  /**
   * Fetch the page following redirects MANUALLY, re-validating every hop against the SSRF guard.
   * jsoup's built-in {@code followRedirects(true)} is disabled because it would chase a redirect to
   * an internal/metadata address without re-checking the host.
   */
  private Document fetchWithSafeRedirects(String startUrl) throws java.io.IOException {
    String url = startUrl;
    for (int hop = 0; hop < MAX_REDIRECTS; hop++) {
      Ssrf.assertSafeUrl(url);
      org.jsoup.Connection.Response resp =
          Jsoup.connect(url)
              .userAgent("HashtagLocalBot/1.0 (+https://local.smalltech.in)")
              .timeout(timeoutMs)
              .maxBodySize(maxBodyBytes)
              .followRedirects(false)
              .ignoreHttpErrors(true)
              .execute();

      int code = resp.statusCode();
      if (code >= 300 && code < 400) {
        String location = resp.header("Location");
        if (location == null || location.isBlank()) {
          throw new java.io.IOException("Redirect with no Location header from " + url);
        }
        // Resolve relative redirects against the current URL, then re-validate next loop.
        url = URI.create(url).resolve(location).toString();
        continue;
      }
      return resp.parse();
    }
    throw new java.io.IOException("Too many redirects starting from " + startUrl);
  }

  private void applyCacheToContent(FeedPostContentEntity content, LinkCache cached) {
    content.setTitle(cached.getTitle());
    if (content.getText() == null || content.getText().isBlank()) {
      content.setText(cached.getDescription());
    }
    content.setEmbedHtml(cached.getEmbedHtml());
    content.setEmbedType(cached.getEmbedType());
    if (cached.getImageMedia() != null) {
      content.setImageMedia(cached.getImageMedia());
    }
    Map<String, Object> data =
        content.getData() == null ? new HashMap<>() : new HashMap<>(content.getData());
    putIfPresent(data, "site_name", cached.getSiteName());
    putIfPresent(data, "favicon_url", cached.getFaviconUrl());
    content.setData(data);
  }

  private static String meta(Document doc, String property) {
    var el = doc.selectFirst("meta[property=" + property + "]");
    if (el == null) {
      el = doc.selectFirst("meta[name=" + property + "]");
    }
    String v = el != null ? el.attr("content") : null;
    return (v == null || v.isBlank()) ? null : v;
  }

  /** First `<link rel="icon">`/`apple-touch-icon` href, if any. */
  private static String faviconHref(Document doc) {
    var el = doc.selectFirst("link[rel~=(?i)icon]");
    if (el == null) {
      el = doc.selectFirst("link[rel=apple-touch-icon]");
    }
    String href = el != null ? el.attr("href") : null;
    return (href == null || href.isBlank()) ? null : href;
  }

  /** Resolve a possibly-relative URL against the page base. Returns null if unusable. */
  private static String absolutize(String maybeRelative, String baseUrl) {
    if (maybeRelative == null || maybeRelative.isBlank()) {
      return null;
    }
    try {
      return URI.create(baseUrl).resolve(maybeRelative.trim()).toString();
    } catch (RuntimeException e) {
      return maybeRelative;
    }
  }

  private static String firstNonBlank(String... values) {
    for (String v : values) {
      if (v != null && !v.isBlank()) {
        return v;
      }
    }
    return null;
  }

  private static void putIfPresent(Map<String, Object> data, String key, String value) {
    if (value != null && !value.isBlank()) {
      data.put(key, value);
    }
  }

  private static String truncate(String s, int max) {
    if (s == null) {
      return null;
    }
    return s.length() <= max ? s : s.substring(0, max);
  }
}
