package org.smalltech.hashtaglocal_backend.service.impl;

import java.net.IDN;
import java.net.InetAddress;
import java.net.URI;
import java.net.UnknownHostException;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.smalltech.hashtaglocal_backend.entity.FeedPostContentEntity;
import org.smalltech.hashtaglocal_backend.entity.FeedPostEntity;
import org.smalltech.hashtaglocal_backend.entity.LinkCache;
import org.smalltech.hashtaglocal_backend.model.LinkEmbedType;
import org.smalltech.hashtaglocal_backend.model.LinkScrapeStatus;
import org.smalltech.hashtaglocal_backend.repository.FeedPostRepository;
import org.smalltech.hashtaglocal_backend.repository.LinkCacheRepository;
import org.smalltech.hashtaglocal_backend.service.LinkScrapeService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Scrapes Open Graph metadata for LINK posts and caches it by canonical URL. SSRF-guarded: only
 * http/https, public hosts, capped size/redirects/timeout. See FEED_DESIGN.md §6.
 *
 * <p>v1 stores the OG image as a URL (in {@code content.data.image_url}); re-hosting it to GCS is
 * deferred — see FEED_DESIGN.md §6/§9.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class LinkScrapeServiceImpl implements LinkScrapeService {

  private static final int MAX_REDIRECTS = 5;

  private final FeedPostRepository feedPostRepository;
  private final LinkCacheRepository linkCacheRepository;
  private final LinkCacheWriter linkCacheWriter;

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

      String title = meta(doc, "og:title");
      if (title == null) {
        title = doc.title();
      }
      String description = meta(doc, "og:description");
      String imageUrl = meta(doc, "og:image");
      String siteName = meta(doc, "og:site_name");

      content.setTitle(truncate(title, 500));
      if (content.getText() == null || content.getText().isBlank()) {
        content.setText(truncate(description, 4000));
      }
      content.setEmbedType(LinkEmbedType.LINK);
      content.setScrapeStatus(LinkScrapeStatus.OK);

      Map<String, Object> data =
          content.getData() == null ? new HashMap<>() : new HashMap<>(content.getData());
      if (imageUrl != null) {
        data.put("image_url", imageUrl);
      }
      if (siteName != null) {
        data.put("site_name", siteName);
      }
      content.setData(data);

      feedPostRepository.save(post);

      // Populate the shared cache for reuse, in a separate transaction so a concurrent duplicate
      // insert can't roll back this post's scrape result.
      linkCacheWriter.saveIfAbsent(
          canonical, truncate(title, 500), truncate(description, 2000), truncate(siteName, 300));
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
      assertSafeUrl(url);
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
  }

  private static String meta(Document doc, String property) {
    var el = doc.selectFirst("meta[property=" + property + "]");
    if (el == null) {
      el = doc.selectFirst("meta[name=" + property + "]");
    }
    String v = el != null ? el.attr("content") : null;
    return (v == null || v.isBlank()) ? null : v;
  }

  private static String truncate(String s, int max) {
    if (s == null) {
      return null;
    }
    return s.length() <= max ? s : s.substring(0, max);
  }

  /** SSRF guard: only http/https to a public host. */
  private static void assertSafeUrl(String url) throws UnknownHostException {
    URI uri = URI.create(url.trim());
    String scheme = uri.getScheme();
    if (scheme == null || !(scheme.equalsIgnoreCase("http") || scheme.equalsIgnoreCase("https"))) {
      throw new IllegalArgumentException("Only http/https URLs may be scraped: " + url);
    }
    String host = uri.getHost();
    if (host == null || host.isBlank()) {
      throw new IllegalArgumentException("URL has no host: " + url);
    }
    String ascii = IDN.toASCII(host).toLowerCase(Locale.ROOT);
    if (ascii.equals("localhost") || ascii.endsWith(".localhost") || ascii.endsWith(".internal")) {
      throw new IllegalArgumentException("Refusing to scrape internal host: " + host);
    }
    for (InetAddress addr : InetAddress.getAllByName(host)) {
      if (addr.isAnyLocalAddress()
          || addr.isLoopbackAddress()
          || addr.isLinkLocalAddress()
          || addr.isSiteLocalAddress()
          || addr.isMulticastAddress()) {
        throw new IllegalArgumentException(
            "Refusing to scrape private/link-local address: " + host);
      }
    }
  }
}
