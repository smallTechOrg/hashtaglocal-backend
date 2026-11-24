package org.smalltech.hashtaglocal_backend.service.ai;

import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.UnknownHostException;
import java.util.Locale;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * SSRF guard for admin-entered AI endpoints: https only, no credentials in the URL, and the host
 * must resolve only to public addresses (no loopback, private, link-local / cloud metadata, CGNAT,
 * unique-local IPv6, multicast or reserved ranges) unless {@code ai.config.allow-private-endpoints}
 * is on.
 *
 * <p>Checked on save, on test and before every call, so a DNS change after saving is caught too. (A
 * DNS answer that changes between this check and the connection itself is not; the endpoint is
 * admin-entered, so that is accepted.)
 */
@Component
public class EndpointValidator {

  static final int MAX_LENGTH = 500;

  /** Resolves a host name; replaceable in tests. */
  interface HostResolver {
    InetAddress[] resolve(String host) throws UnknownHostException;
  }

  private final boolean allowPrivate;
  private final HostResolver resolver;

  @Autowired
  public EndpointValidator(AiConfigProperties properties) {
    this(properties.isAllowPrivateEndpoints(), InetAddress::getAllByName);
  }

  EndpointValidator(boolean allowPrivate, HostResolver resolver) {
    this.allowPrivate = allowPrivate;
    this.resolver = resolver;
  }

  /**
   * @return the trimmed endpoint
   * @throws IllegalArgumentException with a user-facing reason when the endpoint is not allowed
   */
  public String validate(String endpoint) {
    if (endpoint == null || endpoint.isBlank()) {
      throw new IllegalArgumentException("Endpoint is required");
    }
    String trimmed = endpoint.trim();
    if (trimmed.length() > MAX_LENGTH) {
      throw new IllegalArgumentException("Endpoint is too long");
    }
    URI uri;
    try {
      uri = new URI(trimmed);
    } catch (URISyntaxException e) {
      throw new IllegalArgumentException("Endpoint is not a valid URL");
    }
    if (!"https".equalsIgnoreCase(uri.getScheme())) {
      throw new IllegalArgumentException("Endpoint must be an https:// URL");
    }
    if (uri.getRawUserInfo() != null) {
      throw new IllegalArgumentException("Endpoint must not contain credentials");
    }
    if (uri.getRawQuery() != null || uri.getRawFragment() != null) {
      throw new IllegalArgumentException("Endpoint must not have a query string or fragment");
    }
    String host = uri.getHost();
    if (host == null || host.isBlank()) {
      throw new IllegalArgumentException("Endpoint must include a host name");
    }
    if (!allowPrivate) {
      checkPublic(host);
    }
    return trimmed;
  }

  private void checkPublic(String host) {
    String bare =
        host.startsWith("[") && host.endsWith("]") ? host.substring(1, host.length() - 1) : host;
    if (bare.toLowerCase(Locale.ROOT).equals("localhost")
        || bare.toLowerCase(Locale.ROOT).endsWith(".localhost")) {
      throw new IllegalArgumentException("Endpoint host is not a public address");
    }
    InetAddress[] addresses;
    try {
      addresses = resolver.resolve(bare);
    } catch (UnknownHostException e) {
      throw new IllegalArgumentException("Endpoint host could not be resolved");
    }
    if (addresses == null || addresses.length == 0) {
      throw new IllegalArgumentException("Endpoint host could not be resolved");
    }
    for (InetAddress address : addresses) {
      if (!isPublic(address)) {
        throw new IllegalArgumentException("Endpoint host is not a public address");
      }
    }
  }

  static boolean isPublic(InetAddress address) {
    if (address.isAnyLocalAddress()
        || address.isLoopbackAddress()
        || address.isLinkLocalAddress()
        || address.isSiteLocalAddress()
        || address.isMulticastAddress()) {
      return false;
    }
    byte[] b = address.getAddress();
    if (address instanceof Inet4Address) {
      int a0 = b[0] & 0xff;
      int a1 = b[1] & 0xff;
      return !(a0 == 0 // "this" network
          || (a0 == 100 && a1 >= 64 && a1 <= 127) // CGNAT 100.64/10
          || (a0 == 192 && a1 == 0 && (b[2] & 0xff) == 0) // IETF 192.0.0/24
          || (a0 == 198 && (a1 == 18 || a1 == 19)) // benchmarking 198.18/15
          || a0 >= 240); // reserved + broadcast
    }
    if (address instanceof Inet6Address) {
      int first = b[0] & 0xff;
      if ((first & 0xfe) == 0xfc) {
        return false; // unique local fc00::/7
      }
      boolean ipv4Embedded = true; // ::a.b.c.d and ::ffff:a.b.c.d
      for (int i = 0; i < 10; i++) {
        ipv4Embedded &= b[i] == 0;
      }
      boolean mapped = ipv4Embedded && (b[10] == 0 || b[10] == (byte) 0xff) && b[10] == b[11];
      if (mapped) {
        try {
          return isPublic(InetAddress.getByAddress(new byte[] {b[12], b[13], b[14], b[15]}));
        } catch (UnknownHostException e) {
          return false;
        }
      }
      // 64:ff9b::/96 NAT64 can reach internal IPv4 addresses.
      return !(first == 0x00
          && (b[1] & 0xff) == 0x64
          && (b[2] & 0xff) == 0xff
          && (b[3] & 0xff) == 0x9b);
    }
    return false;
  }
}
