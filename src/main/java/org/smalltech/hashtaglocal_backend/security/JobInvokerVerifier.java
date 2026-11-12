package org.smalltech.hashtaglocal_backend.security;

import com.google.api.client.json.webtoken.JsonWebToken;
import com.google.auth.oauth2.TokenVerifier;
import java.util.Arrays;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Authenticates calls to the job endpoints.
 *
 * <p>The service is public, so each job call must carry a Google-signed OIDC token whose audience
 * is {@code jobs.oidc.audience} and whose email is one of {@code jobs.oidc.allowed-invokers}. Cloud
 * Scheduler adds that token itself when its job is created with an OIDC service account. With
 * either setting empty every call is refused.
 */
@Component
public class JobInvokerVerifier {

  private final String audience;
  private final Set<String> allowedInvokers;

  public JobInvokerVerifier(
      @Value("${jobs.oidc.audience:}") String audience,
      @Value("${jobs.oidc.allowed-invokers:}") String allowedInvokers) {
    this.audience = audience == null ? "" : audience.trim();
    this.allowedInvokers =
        Arrays.stream(allowedInvokers == null ? new String[0] : allowedInvokers.split(","))
            .map(s -> s.trim().toLowerCase(Locale.ROOT))
            .filter(s -> !s.isEmpty())
            .collect(Collectors.toSet());
  }

  /**
   * Checks the Authorization header and returns the caller's email.
   *
   * @throws SecurityException when the call must be refused
   */
  public String verify(String authHeader) {
    if (audience.isEmpty() || allowedInvokers.isEmpty()) {
      throw new SecurityException("job endpoints are not configured");
    }
    String prefix = "Bearer ";
    if (authHeader == null || !authHeader.regionMatches(true, 0, prefix, 0, prefix.length())) {
      throw new SecurityException("missing bearer token");
    }
    String token = authHeader.substring(prefix.length()).trim();
    if (token.isEmpty()) {
      throw new SecurityException("missing bearer token");
    }

    JsonWebToken.Payload claims;
    try {
      claims = TokenVerifier.newBuilder().setAudience(audience).build().verify(token).getPayload();
    } catch (Exception e) { // expired, wrong audience, bad signature, ...
      throw new SecurityException("invalid token: " + e.getMessage(), e);
    }

    Object email = claims.get("email");
    String caller = email == null ? "" : email.toString().toLowerCase(Locale.ROOT);
    boolean verified = Boolean.TRUE.equals(claims.get("email_verified"));
    if (!verified || !allowedInvokers.contains(caller)) {
      throw new SecurityException(
          "caller " + (caller.isEmpty() ? "<no email>" : caller) + " is not allowed");
    }
    return caller;
  }
}
