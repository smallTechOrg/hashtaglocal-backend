package org.smalltech.hashtaglocal_backend.service;

import java.net.URI;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Pattern;
import lombok.extern.slf4j.Slf4j;
import org.smalltech.hashtaglocal_backend.entity.AiProviderConfigAuditEntity;
import org.smalltech.hashtaglocal_backend.entity.AiProviderConfigEntity;
import org.smalltech.hashtaglocal_backend.entity.UserEntity;
import org.smalltech.hashtaglocal_backend.exception.DownstreamServiceException;
import org.smalltech.hashtaglocal_backend.model.request.AiProviderConfigRequest;
import org.smalltech.hashtaglocal_backend.model.response.AiConnectionTestData;
import org.smalltech.hashtaglocal_backend.model.response.AiProviderConfigData;
import org.smalltech.hashtaglocal_backend.repository.AiProviderConfigAuditRepository;
import org.smalltech.hashtaglocal_backend.repository.AiProviderConfigRepository;
import org.smalltech.hashtaglocal_backend.repository.UserRepository;
import org.smalltech.hashtaglocal_backend.security.SecretCipher;
import org.smalltech.hashtaglocal_backend.service.ai.AiConfigResolver;
import org.smalltech.hashtaglocal_backend.service.ai.AiConfigResolver.ActiveAiConfig;
import org.smalltech.hashtaglocal_backend.service.ai.AiFailure;
import org.smalltech.hashtaglocal_backend.service.ai.AiFallbackChain;
import org.smalltech.hashtaglocal_backend.service.ai.AiProvider;
import org.smalltech.hashtaglocal_backend.service.ai.AiResult;
import org.smalltech.hashtaglocal_backend.service.ai.EndpointValidator;
import org.smalltech.hashtaglocal_backend.service.ai.SecretRedactor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Admin management of the AI provider used for quiz generation: read, save, reset to {@code .env},
 * and test. The API key is encrypted before it is stored and is never returned.
 */
@Service
@Slf4j
public class AiProviderConfigService {

  static final String TEST_PROMPT = "Reply with the single word OK.";
  static final int TEST_MAX_TOKENS = 32;

  private static final Pattern MODEL_NAME = Pattern.compile("[A-Za-z0-9._:/@-]{1,200}");
  private static final int MIN_KEY_LENGTH = 8;

  private final AiProviderConfigRepository repository;
  private final AiProviderConfigAuditRepository auditRepository;
  private final UserRepository userRepository;
  private final SecretCipher cipher;
  private final EndpointValidator endpointValidator;
  private final AiFallbackChain chain;
  private final AiConfigResolver resolver;
  private final Clock clock;

  @Autowired
  public AiProviderConfigService(
      AiProviderConfigRepository repository,
      AiProviderConfigAuditRepository auditRepository,
      UserRepository userRepository,
      SecretCipher cipher,
      EndpointValidator endpointValidator,
      AiFallbackChain chain,
      AiConfigResolver resolver) {
    this(
        repository,
        auditRepository,
        userRepository,
        cipher,
        endpointValidator,
        chain,
        resolver,
        Clock.systemDefaultZone());
  }

  AiProviderConfigService(
      AiProviderConfigRepository repository,
      AiProviderConfigAuditRepository auditRepository,
      UserRepository userRepository,
      SecretCipher cipher,
      EndpointValidator endpointValidator,
      AiFallbackChain chain,
      AiConfigResolver resolver,
      Clock clock) {
    this.repository = repository;
    this.auditRepository = auditRepository;
    this.userRepository = userRepository;
    this.cipher = cipher;
    this.endpointValidator = endpointValidator;
    this.chain = chain;
    this.resolver = resolver;
    this.clock = clock;
  }

  @Transactional(readOnly = true)
  public AiProviderConfigData get() {
    return toData(repository.findById(AiProviderConfigEntity.SINGLETON_ID));
  }

  /**
   * Validates and saves the config. A blank API key keeps the saved one (only for the same
   * provider). Everything is validated and the key encrypted before the row is touched, so a bad
   * request never leaves a half-written config.
   */
  @Transactional
  public AiProviderConfigData save(AiProviderConfigRequest request, Long adminUserId) {
    AiProvider provider = requireProvider(request.getProvider());
    String endpoint =
        validateEndpoint(
            provider,
            isBlank(request.getEndpoint()) ? provider.defaultEndpoint() : request.getEndpoint());
    String model = requireModel(request.getModel());
    String newKey = cleanKey(request.getApiKey());

    Optional<AiProviderConfigEntity> existing =
        repository.findById(AiProviderConfigEntity.SINGLETON_ID);
    String ciphertext = null;
    if (newKey == null) {
      if (existing.isEmpty()) {
        throw new IllegalArgumentException("API key is required");
      }
      if (!existing.get().getProvider().equals(provider.name())) {
        throw new IllegalArgumentException(
            "Enter the API key for "
                + provider.displayName()
                + "; the saved key belongs to another provider");
      }
      requireSameServer(existing.get(), endpoint);
    } else {
      requireEncryption();
      ciphertext = cipher.encrypt(newKey, AiProviderConfigEntity.KEY_CIPHER_CONTEXT);
    }

    AiProviderConfigEntity config =
        existing.orElseGet(
            () -> AiProviderConfigEntity.builder().id(AiProviderConfigEntity.SINGLETON_ID).build());
    config.setProvider(provider.name());
    config.setEndpoint(endpoint);
    config.setModel(model);
    if (ciphertext != null) {
      config.setApiKeyCiphertext(ciphertext);
      config.setApiKeyLast4(last4(newKey));
    }
    LocalDateTime now = LocalDateTime.now(clock);
    config.setUpdatedBy(adminUserId);
    config.setUpdatedAt(now);
    AiProviderConfigEntity saved = repository.save(config);

    auditRepository.save(
        AiProviderConfigAuditEntity.builder()
            .action(AiProviderConfigAuditEntity.Action.SAVED)
            .provider(saved.getProvider())
            .endpoint(saved.getEndpoint())
            .model(saved.getModel())
            .keyChanged(ciphertext != null)
            .changedBy(adminUserId)
            .changedAt(now)
            .build());
    log.info(
        "AI provider config saved by user {}: {}/{} at {} (key {})",
        adminUserId,
        saved.getProvider(),
        saved.getModel(),
        saved.getEndpoint(),
        ciphertext != null ? "replaced" : "kept");
    invalidateAfterCommit();
    return toData(Optional.of(saved));
  }

  /** Deletes the saved config, so quiz generation goes back to the .env chain. */
  @Transactional
  public AiProviderConfigData reset(Long adminUserId) {
    Optional<AiProviderConfigEntity> existing =
        repository.findById(AiProviderConfigEntity.SINGLETON_ID);
    if (existing.isPresent()) {
      repository.delete(existing.get());
      auditRepository.save(
          AiProviderConfigAuditEntity.builder()
              .action(AiProviderConfigAuditEntity.Action.RESET)
              .keyChanged(true)
              .changedBy(adminUserId)
              .changedAt(LocalDateTime.now(clock))
              .build());
      log.info("AI provider config reset to .env by user {}", adminUserId);
      invalidateAfterCommit();
    }
    return toData(Optional.empty());
  }

  /**
   * Makes one minimal call with the submitted values, filling any blank field from the saved config
   * (same provider only). Saves nothing. An empty reply still counts as connected: the key, model
   * and endpoint all worked.
   */
  @Transactional(readOnly = true)
  public AiConnectionTestData test(AiProviderConfigRequest request) {
    Optional<AiProviderConfigEntity> saved =
        repository.findById(AiProviderConfigEntity.SINGLETON_ID);
    AiProvider provider =
        requireProvider(
            isBlank(request.getProvider())
                ? saved.map(AiProviderConfigEntity::getProvider).orElse(null)
                : request.getProvider());
    Optional<AiProviderConfigEntity> sameProvider =
        saved.filter(s -> s.getProvider().equals(provider.name()));

    String endpoint =
        validateEndpoint(
            provider,
            !isBlank(request.getEndpoint())
                ? request.getEndpoint()
                : sameProvider
                    .map(AiProviderConfigEntity::getEndpoint)
                    .orElse(provider.defaultEndpoint()));
    String model =
        requireModel(
            !isBlank(request.getModel())
                ? request.getModel()
                : sameProvider.map(AiProviderConfigEntity::getModel).orElse(null));
    String key = cleanKey(request.getApiKey());
    if (key == null) {
      if (sameProvider.isEmpty()) {
        throw new IllegalArgumentException("API key is required");
      }
      requireSameServer(sameProvider.get(), endpoint);
      key = decryptSaved(sameProvider.get());
    }

    ActiveAiConfig config = new ActiveAiConfig(provider.name(), endpoint, model, key);
    long start = System.nanoTime();
    AiResult result = chain.callConfigured(config, TEST_PROMPT, TEST_MAX_TOKENS);
    long latencyMs = (System.nanoTime() - start) / 1_000_000;

    boolean connected = result.isSuccess() || result.failure() == AiFailure.INVALID_RESPONSE;
    String message =
        connected
            ? "Connected to " + provider.displayName() + " (" + model + ")"
            : AiFallbackChain.describe(result.failure())
                + ": "
                + SecretRedactor.redact(result.detail(), key);
    log.info(
        "AI provider test {}/{}: {}", provider.name(), model, connected ? "ok" : result.failure());
    return AiConnectionTestData.builder()
        .success(connected)
        .provider(provider.name())
        .endpoint(endpoint)
        .model(model)
        .latencyMs(latencyMs)
        .message(message)
        .build();
  }

  private String decryptSaved(AiProviderConfigEntity saved) {
    requireEncryption();
    try {
      return cipher.decrypt(saved.getApiKeyCiphertext(), AiProviderConfigEntity.KEY_CIPHER_CONTEXT);
    } catch (SecretCipher.SecretDecryptionException e) {
      throw new DownstreamServiceException(
          HttpStatus.CONFLICT,
          "KEY_UNREADABLE",
          "The saved API key could not be decrypted. Enter the key again.");
    }
  }

  private AiProviderConfigData toData(Optional<AiProviderConfigEntity> saved) {
    AiProviderConfigData.AiProviderConfigDataBuilder data =
        AiProviderConfigData.builder()
            .encryptionConfigured(cipher.isConfigured())
            .envChain(chain.envChainSummary())
            .availableProviders(
                chain.providers().values().stream()
                    .map(
                        p ->
                            AiProviderConfigData.ProviderOption.builder()
                                .name(p.name())
                                .displayName(p.displayName())
                                .defaultEndpoint(p.defaultEndpoint())
                                .build())
                    .toList());
    if (saved.isEmpty()) {
      return data.source(AiProviderConfigData.Source.ENV)
          .hasKey(!chain.envChainSummary().isEmpty())
          .build();
    }
    AiProviderConfigEntity c = saved.get();
    return data.source(AiProviderConfigData.Source.DATABASE)
        .provider(c.getProvider())
        .endpoint(c.getEndpoint())
        .model(c.getModel())
        .hasKey(c.getApiKeyCiphertext() != null && !c.getApiKeyCiphertext().isBlank())
        .keyLast4(c.getApiKeyLast4())
        .updatedAt(c.getUpdatedAt())
        .updatedBy(c.getUpdatedBy())
        .updatedByName(
            c.getUpdatedBy() == null
                ? null
                : userRepository
                    .findById(c.getUpdatedBy())
                    .map(UserEntity::getUsername)
                    .orElse(null))
        .build();
  }

  /**
   * A saved key may only be sent to the server it was saved for. Otherwise an admin could point the
   * endpoint at a server they control, leave the key blank, and have the saved key sent there. A
   * different path on the same server is fine.
   */
  private static void requireSameServer(AiProviderConfigEntity saved, String endpoint) {
    if (!origin(saved.getEndpoint()).equals(origin(endpoint))) {
      throw new IllegalArgumentException(
          "Enter the API key again to use a different endpoint host. The saved key is only sent to "
              + origin(saved.getEndpoint()).replaceFirst(":443$", ""));
    }
  }

  /** {@code scheme://host:port} of an (already validated) endpoint, lower-cased. */
  static String origin(String endpoint) {
    URI uri = URI.create(endpoint.trim());
    String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
    String host = uri.getHost() == null ? "" : uri.getHost().toLowerCase(Locale.ROOT);
    int port = uri.getPort() != -1 ? uri.getPort() : ("https".equals(scheme) ? 443 : 80);
    return scheme + "://" + host + ":" + port;
  }

  /** The validated endpoint; a provider with no default (openai-compatible) must be given one. */
  private String validateEndpoint(AiProvider provider, String endpoint) {
    if (isBlank(endpoint)) {
      throw new IllegalArgumentException("Endpoint is required for " + provider.displayName());
    }
    return endpointValidator.validate(endpoint);
  }

  private AiProvider requireProvider(String name) {
    if (isBlank(name)) {
      throw new IllegalArgumentException("Provider is required");
    }
    AiProvider provider = chain.providers().get(name.trim().toLowerCase(java.util.Locale.ROOT));
    if (provider == null) {
      throw new IllegalArgumentException(
          "Unknown provider. Allowed: " + String.join(", ", chain.providers().keySet()));
    }
    return provider;
  }

  private static String requireModel(String model) {
    if (isBlank(model)) {
      throw new IllegalArgumentException("Model is required");
    }
    String trimmed = model.trim();
    if (!MODEL_NAME.matcher(trimmed).matches()) {
      throw new IllegalArgumentException(
          "Model may only contain letters, digits and . _ : / @ - (no spaces)");
    }
    return trimmed;
  }

  /** The trimmed key, or null when blank (meaning "keep the saved key"). */
  private static String cleanKey(String key) {
    if (isBlank(key)) {
      return null;
    }
    String trimmed = key.trim();
    if (trimmed.length() < MIN_KEY_LENGTH || trimmed.chars().anyMatch(Character::isWhitespace)) {
      throw new IllegalArgumentException("API key looks invalid");
    }
    return trimmed;
  }

  private void requireEncryption() {
    if (!cipher.isConfigured()) {
      throw new DownstreamServiceException(
          HttpStatus.SERVICE_UNAVAILABLE,
          "ENCRYPTION_NOT_CONFIGURED",
          "AI_CONFIG_ENCRYPTION_KEY is not set on the server, so API keys cannot be stored.");
    }
  }

  private static String last4(String key) {
    return key.substring(key.length() - 4);
  }

  private static boolean isBlank(String value) {
    return value == null || value.isBlank();
  }

  /** Clears the resolver cache once the new row is committed (so no reader caches the old one). */
  private void invalidateAfterCommit() {
    if (TransactionSynchronizationManager.isSynchronizationActive()) {
      TransactionSynchronizationManager.registerSynchronization(
          new TransactionSynchronization() {
            @Override
            public void afterCommit() {
              resolver.invalidate();
            }
          });
    } else {
      resolver.invalidate();
    }
  }
}
