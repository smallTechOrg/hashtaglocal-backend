package org.smalltech.hashtaglocal_backend.util;

import java.net.URI;
import java.util.Locale;

/** URL normalization for link dedupe (the {@code link_cache} key). See FEED_DESIGN.md §6. */
public final class LinkUrls {

  private LinkUrls() {}

  /**
   * Produce a stable canonical form: lower-cased scheme + host, no default port, no trailing slash,
   * tracking query params dropped. Best-effort — returns a trimmed original if parsing fails.
   */
  public static String canonicalize(String raw) {
    if (raw == null) {
      return null;
    }
    String trimmed = raw.trim();
    try {
      URI uri = URI.create(trimmed);
      String scheme = uri.getScheme() == null ? "https" : uri.getScheme().toLowerCase(Locale.ROOT);
      String host = uri.getHost() == null ? "" : uri.getHost().toLowerCase(Locale.ROOT);
      String path = uri.getPath() == null ? "" : uri.getPath();
      if (path.endsWith("/") && path.length() > 1) {
        path = path.substring(0, path.length() - 1);
      }
      String query = stripTrackingParams(uri.getQuery());
      StringBuilder sb = new StringBuilder(scheme).append("://").append(host).append(path);
      if (query != null && !query.isBlank()) {
        sb.append('?').append(query);
      }
      return sb.toString();
    } catch (RuntimeException e) {
      return trimmed;
    }
  }

  private static String stripTrackingParams(String query) {
    if (query == null || query.isBlank()) {
      return null;
    }
    StringBuilder kept = new StringBuilder();
    for (String pair : query.split("&")) {
      String key = pair.contains("=") ? pair.substring(0, pair.indexOf('=')) : pair;
      String lk = key.toLowerCase(Locale.ROOT);
      if (lk.startsWith("utm_") || lk.equals("fbclid") || lk.equals("gclid")) {
        continue;
      }
      if (kept.length() > 0) {
        kept.append('&');
      }
      kept.append(pair);
    }
    return kept.toString();
  }
}
