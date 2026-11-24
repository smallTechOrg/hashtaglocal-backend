package org.smalltech.hashtaglocal_backend.service.ai;

import java.time.Duration;
import lombok.Data;
import lombok.ToString;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * {@code ai.config}: settings for the AI provider config that admins edit in the Ops Dashboard
 * (stored in {@code ai_provider_config}).
 */
@Configuration
@ConfigurationProperties(prefix = "ai.config")
@Data
public class AiConfigProperties {

  /**
   * Base64 of a 32-byte AES key ({@code AI_CONFIG_ENCRYPTION_KEY}) used to encrypt stored API keys.
   * Comes from the environment / secret manager only. When empty, saving a new key is refused and
   * quiz generation keeps using the {@code .env} chain.
   */
  @ToString.Exclude private String encryptionKey = "";

  /**
   * Allow endpoints on private, loopback or link-local addresses. Off by default (SSRF guard); only
   * for a self-hosted, OpenAI-compatible server on a private network.
   */
  private boolean allowPrivateEndpoints = false;

  /**
   * How long each instance caches the saved config. A save clears the cache on the instance that
   * handled it; other instances pick the change up within this time.
   */
  private Duration cacheTtl = Duration.ofSeconds(30);
}
