package org.smalltech.hashtaglocal_backend.service.ai;

import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.smalltech.hashtaglocal_backend.entity.AiProviderConfigEntity;
import org.smalltech.hashtaglocal_backend.repository.AiProviderConfigRepository;
import org.smalltech.hashtaglocal_backend.security.SecretCipher;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * Reads the admin-saved AI provider config at request time, with a short in-memory cache ({@code
 * ai.config.cache-ttl}) that {@link #invalidate()} clears on save. Empty means "no saved config":
 * quiz generation then uses the {@code .env} chain.
 *
 * <p>The cache holds the ciphertext; the key is decrypted per use and never kept in a field.
 */
@Component
@Slf4j
public class AiConfigResolver {

  /** The connection quiz generation should use. {@code toString()} never shows the key. */
  public record ActiveAiConfig(String name, String endpoint, String model, String apiKey) {

    /** Fits {@code quizzes.generated_by} (100 chars). */
    private static final int MAX_LABEL = 100;

    /** "name/model", recorded on each quiz as who wrote it. */
    public String label() {
      String label = (name == null || name.isBlank() ? "ai" : name.trim()) + "/" + model;
      return label.length() <= MAX_LABEL ? label : label.substring(0, MAX_LABEL);
    }

    @Override
    public String toString() {
      return "ActiveAiConfig[" + name + ", " + endpoint + ", " + model + "]";
    }
  }

  private record Cached(Optional<AiProviderConfigEntity> config, Instant loadedAt) {}

  private final AiProviderConfigRepository repository;
  private final SecretCipher cipher;
  private final AiConfigProperties properties;
  private final Clock clock;
  private volatile Cached cached;

  @Autowired
  public AiConfigResolver(
      AiProviderConfigRepository repository, SecretCipher cipher, AiConfigProperties properties) {
    this(repository, cipher, properties, Clock.systemUTC());
  }

  AiConfigResolver(
      AiProviderConfigRepository repository,
      SecretCipher cipher,
      AiConfigProperties properties,
      Clock clock) {
    this.repository = repository;
    this.cipher = cipher;
    this.properties = properties;
    this.clock = clock;
  }

  /**
   * The saved config with its key decrypted, or empty when none is saved.
   *
   * @throws AiProviderException when a config is saved but its key cannot be decrypted (missing or
   *     changed {@code AI_CONFIG_ENCRYPTION_KEY}). This is deliberately not a silent fallback to
   *     {@code .env}: an admin chose this provider, so the caller gets a clear error.
   */
  public Optional<ActiveAiConfig> active() {
    Optional<AiProviderConfigEntity> config = load();
    if (config.isEmpty()) {
      return Optional.empty();
    }
    AiProviderConfigEntity c = config.get();
    if (c.getApiKeyCiphertext() == null) {
      throw new AiProviderException(
          "The AI provider saved in the Ops Dashboard has no API key. Enter the key again.");
    }
    if (!cipher.isConfigured()) {
      throw new AiProviderException(
          "An AI provider is configured in the Ops Dashboard, but AI_CONFIG_ENCRYPTION_KEY is not"
              + " set on the server, so its key cannot be read.");
    }
    try {
      String key =
          cipher.decrypt(c.getApiKeyCiphertext(), AiProviderConfigEntity.KEY_CIPHER_CONTEXT);
      return Optional.of(new ActiveAiConfig(c.getName(), c.getEndpoint(), c.getModel(), key));
    } catch (SecretCipher.SecretDecryptionException e) {
      log.error("Saved AI provider key could not be decrypted (wrong AI_CONFIG_ENCRYPTION_KEY?)");
      throw new AiProviderException(
          "The saved AI provider key could not be decrypted. Check AI_CONFIG_ENCRYPTION_KEY, or"
              + " re-enter the key in the Ops Dashboard.");
    }
  }

  /**
   * The config in effect (from cache), without decrypting anything: the newest row, unless that is
   * a "removed" row.
   */
  public Optional<AiProviderConfigEntity> load() {
    Cached current = cached;
    Instant now = clock.instant();
    if (current == null || !now.isBefore(current.loadedAt().plus(properties.getCacheTtl()))) {
      current =
          new Cached(
              repository.findTopByOrderByIdDesc().filter(AiProviderConfigEntity::isActive), now);
      cached = current;
    }
    return current.config();
  }

  /** Drops the cache, so the next call reads the database. Call after every save. */
  public void invalidate() {
    cached = null;
  }
}
