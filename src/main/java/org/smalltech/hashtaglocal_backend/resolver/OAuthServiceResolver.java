package org.smalltech.hashtaglocal_backend.resolver;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.smalltech.hashtaglocal_backend.service.OAuthService;
import org.springframework.stereotype.Component;

@Component
public class OAuthServiceResolver {

  private final Map<String, OAuthService> servicesByProvider;

  public OAuthServiceResolver(List<OAuthService> services) {
    this.servicesByProvider =
        services.stream()
            .collect(
                Collectors.toUnmodifiableMap(
                    service -> normalize(service.getProviderType()), Function.identity()));
  }

  public OAuthService resolve(String providerType) {
    OAuthService service = servicesByProvider.get(normalize(providerType));
    if (service == null) {
      throw new IllegalArgumentException("Unsupported auth provider: " + providerType);
    }
    return service;
  }

  private static String normalize(String providerType) {
    return providerType.toLowerCase(Locale.ROOT);
  }
}
