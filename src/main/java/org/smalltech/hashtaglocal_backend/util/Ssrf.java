package org.smalltech.hashtaglocal_backend.util;

import java.net.IDN;
import java.net.InetAddress;
import java.net.URI;
import java.net.UnknownHostException;
import java.util.Locale;

/**
 * SSRF guard for outbound fetches of user-supplied URLs (link scraping, OG-image re-hosting). Only
 * http/https to public hosts are allowed; localhost, private/link-local/loopback/multicast targets
 * are refused. See FEED_DESIGN.md §6.
 */
public final class Ssrf {

  private Ssrf() {}

  /** Throws {@link IllegalArgumentException} if {@code url} is not a safe public http(s) target. */
  public static void assertSafeUrl(String url) throws UnknownHostException {
    URI uri = URI.create(url.trim());
    String scheme = uri.getScheme();
    if (scheme == null || !(scheme.equalsIgnoreCase("http") || scheme.equalsIgnoreCase("https"))) {
      throw new IllegalArgumentException("Only http/https URLs are allowed: " + url);
    }
    String host = uri.getHost();
    if (host == null || host.isBlank()) {
      throw new IllegalArgumentException("URL has no host: " + url);
    }
    String ascii = IDN.toASCII(host).toLowerCase(Locale.ROOT);
    if (ascii.equals("localhost") || ascii.endsWith(".localhost") || ascii.endsWith(".internal")) {
      throw new IllegalArgumentException("Refusing to reach internal host: " + host);
    }
    for (InetAddress addr : InetAddress.getAllByName(host)) {
      if (addr.isAnyLocalAddress()
          || addr.isLoopbackAddress()
          || addr.isLinkLocalAddress()
          || addr.isSiteLocalAddress()
          || addr.isMulticastAddress()) {
        throw new IllegalArgumentException("Refusing to reach private/link-local address: " + host);
      }
    }
  }
}
