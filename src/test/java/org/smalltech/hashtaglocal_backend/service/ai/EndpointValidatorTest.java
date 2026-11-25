package org.smalltech.hashtaglocal_backend.service.ai;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class EndpointValidatorTest {

  // Fake DNS, so the test never depends on the network.
  private static final Map<String, String> DNS =
      Map.of(
          "api.example.com", "93.184.216.34",
          "internal.example.com", "10.0.0.5",
          "metadata.example.com", "169.254.169.254",
          "cgnat.example.com", "100.64.1.1",
          "v6private.example.com", "fd00::1",
          "mapped.example.com", "::ffff:127.0.0.1");

  private final EndpointValidator validator =
      new EndpointValidator(false, EndpointValidatorTest::resolve);

  @Test
  void acceptsAPublicHttpsEndpoint() {
    assertEquals(
        "https://api.example.com/v1/messages",
        validator.validate("  https://api.example.com/v1/messages "));
  }

  @Test
  void lowerCasesTheScheme() {
    // The ai_configs CHECK constraint (LIKE 'https://%') is case-sensitive.
    assertEquals("https://api.example.com/v1", validator.validate("HTTPS://api.example.com/v1"));
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "http://api.example.com/v1",
        "ftp://api.example.com",
        "https://user:pass@api.example.com/v1",
        "https://api.example.com/v1?x=1",
        "https:///nohost",
        "not a url",
        "https://localhost/v1",
        "https://127.0.0.1/v1",
        "https://[::1]/v1",
        "https://internal.example.com/v1",
        "https://metadata.example.com/latest",
        "https://cgnat.example.com/v1",
        "https://v6private.example.com/v1",
        "https://mapped.example.com/v1",
        "https://10.1.2.3/v1",
        "https://192.168.1.10/v1",
        "https://unknown.example.com/v1"
      })
  void rejectsUnsafeEndpoints(String endpoint) {
    assertThrows(IllegalArgumentException.class, () -> validator.validate(endpoint));
  }

  @Test
  void privateEndpointsAllowedOnlyWhenExplicitlyEnabled() {
    EndpointValidator permissive = new EndpointValidator(true, EndpointValidatorTest::resolve);
    assertEquals(
        "https://internal.example.com/v1", permissive.validate("https://internal.example.com/v1"));
    // https is still required.
    assertThrows(
        IllegalArgumentException.class, () -> permissive.validate("http://internal.example.com"));
  }

  private static InetAddress[] resolve(String host) throws UnknownHostException {
    String ip = DNS.get(host);
    if (ip == null) {
      // IP literals resolve to themselves without DNS.
      if (host.matches("[0-9.]+") || host.contains(":")) {
        return new InetAddress[] {InetAddress.getByName(host)};
      }
      throw new UnknownHostException(host);
    }
    return new InetAddress[] {InetAddress.getByName(ip)};
  }
}
