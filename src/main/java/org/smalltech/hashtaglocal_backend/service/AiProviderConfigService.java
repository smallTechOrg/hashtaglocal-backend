package org.smalltech.hashtaglocal_backend.service;

import java.net.URI;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Pattern;
import lombok.extern.slf4j.Slf4j;
import org.smalltech.hashtaglocal_backend.entity.AiProviderConfigEntity;
import org.smalltech.hashtaglocal_backend.entity.UserEntity;
import org.smalltech.hashtaglocal_backend.exception.DownstreamServiceException;
import org.smalltech.hashtaglocal_backend.model.request.AiProviderConfigRequest;
import org.smalltech.hashtaglocal_backend.model.response.AiConnectionTestData;
import org.smalltech.hashtaglocal_backend.model.response.AiProviderConfigData;
import org.smalltech.hashtaglocal_backend.repository.AiProviderConfigRepository;
import org.smalltech.hashtaglocal_backend.repository.UserRepository;
import org.smalltech.hashtaglocal_backend.security.SecretCipher;
import org.smalltech.hashtaglocal_backend.service.ai.AiConfigResolver;
import org.smalltech.hashtaglocal_backend.service.ai.AiConfigResolver.ActiveAiConfig;
import org.smalltech.hashtaglocal_backend.service.ai.AiFailure;
import org.smalltech.hashtaglocal_backend.service.ai.AiFallbackChain;
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
 * Admin management of the AI connection used for quiz generation: read, save, remove (back to
 * {@code .env}), and test. A connection is a name the admin chooses plus an OpenAI-compatible
 * endpoint, a model and an API key. The key is encrypted before it is stored and is never returned.
 *
 * <p>{@code ai_configs} is both the current connection and its history: Save and Remove each add a
 * row, and the newest row is in effect. Only the newest row keeps an encrypted key.
 */
@Service
@Slf4j
public class AiProviderConfigService {

  static final String TEST_PROMPT = "Reply with the single word OK.";
  static final int TEST_MAX_TOKENS = 32;

  private static final Pattern MODEL_NAME = Pattern.compile("[A-Za-z0-9._:/@-]{1,200}");
  private static final int MAX_NAME_LENGTH = 100;
  private static final int MIN_KEY_LENGTH = 8;

  private final AiProviderConfigRepository repository;
  private final UserRepository userRepository;
  private final SecretCipher cipher;
  private final EndpointValidator endpointValidator;
  private final AiFallbackChain chain;
  private final AiConfigResolver resolver;

  @Autowired
  public AiProviderConfigService(
      AiProviderConfigRepository repository,
      UserRepository userRepository,
      SecretCipher cipher,
      EndpointValidator endpointValidator,
      AiFallbackChain chain,
      AiConfigResolver resolver) {
    this.repository = repository;
    this.userRepository = userRepository;
    this.cipher = cipher;
    this.endpointValidator = endpointValidator;
    this.chain = chain;
    this.resolver = resolver;
  }

  @Transactional(readOnly = true)
  public AiProviderConfigData get() {
    return toData(current());
  }

  /** The connection in effect: the newest row, unless it is a "removed" row. Read fresh. */
  private Optional<AiProviderConfigEntity> current() {
    return repository.findTopByOrderByIdDesc().filter(AiProviderConfigEntity::isActive);
  }

  /**
   * Validates the connection and adds it as the newest row. A blank API key keeps the saved one
   * (same host only). Everything is validated and the key encrypted before anything is written, so
   * a bad request never leaves a half-written config.
   */
  @Transactional
  public AiProviderConfigData save(AiProviderConfigRequest request, Long adminUserId) {
    String name = requireName(request.getName());
    String endpoint = validateEndpoint(request.getEndpoint());
    String model = requireModel(request.getModel());
    String newKey = cleanKey(request.getApiKey());

    Optional<AiProviderConfigEntity> existing = current();
    String ciphertext = null;
    if (newKey == null) {
      if (existing.isEmpty()) {
        throw new IllegalArgumentException("API key is required");
      }
      requireSameServer(existing.get(), endpoint);
    } else {
      requireEncryption();
      ciphertext = cipher.encrypt(newKey, AiProviderConfigEntity.KEY_CIPHER_CONTEXT);
    }

    boolean keyChanged = ciphertext != null;
    AiProviderConfigEntity saved =
        repository.save(
            AiProviderConfigEntity.builder()
                .name(name)
                .endpoint(endpoint)
                .model(model)
                .apiKeyCiphertext(keyChanged ? ciphertext : existing.get().getApiKeyCiphertext())
                .apiKeyLast4(keyChanged ? last4(newKey) : existing.get().getApiKeyLast4())
                .keyChanged(keyChanged)
                .updatedBy(adminUserId)
                .build());
    // The key now lives on the new row only; older rows keep their history but not their keys.
    repository.clearKeysExcept(saved.getId());
    log.info(
        "AI connection '{}' saved by user {}: {} at {} (key {})",
        saved.getName(),
        adminUserId,
        saved.getModel(),
        saved.getEndpoint(),
        keyChanged ? "replaced" : "kept");
    invalidateAfterCommit();
    return toData(Optional.of(saved));
  }

  /**
   * Adds a "removed" row (no connection, no key) and clears the key from every older row, so quiz
   * generation goes back to the .env chain and no saved key is left in the database.
   */
  @Transactional
  public AiProviderConfigData reset(Long adminUserId) {
    if (current().isPresent()) {
      AiProviderConfigEntity removed =
          repository.save(
              AiProviderConfigEntity.builder().keyChanged(true).updatedBy(adminUserId).build());
      repository.clearKeysExcept(removed.getId());
      log.info("AI connection removed by user {}; quiz generation uses .env", adminUserId);
      invalidateAfterCommit();
    }
    return toData(Optional.empty());
  }

  /**
   * Makes one minimal call with the submitted values, filling any blank field from the saved
   * connection. Saves nothing. An empty reply still counts as connected: the key, model and
   * endpoint all worked.
   *
   * <p>Deliberately not {@code @Transactional}: the provider call can take up to the client's read
   * timeout, and a transaction would hold a database connection for all of it. The saved row is
   * read in the repository's own short transaction, and the entity has no lazy fields.
   */
  public AiConnectionTestData test(AiProviderConfigRequest request) {
    Optional<AiProviderConfigEntity> saved = current();
    String name =
        !isBlank(request.getName())
            ? requireName(request.getName())
            : saved.map(AiProviderConfigEntity::getName).orElse(null);
    String endpoint =
        validateEndpoint(
            !isBlank(request.getEndpoint())
                ? request.getEndpoint()
                : saved.map(AiProviderConfigEntity::getEndpoint).orElse(null));
    String model =
        requireModel(
            !isBlank(request.getModel())
                ? request.getModel()
                : saved.map(AiProviderConfigEntity::getModel).orElse(null));
    String key = cleanKey(request.getApiKey());
    if (key == null) {
      if (saved.isEmpty()) {
        throw new IllegalArgumentException("API key is required");
      }
      requireSameServer(saved.get(), endpoint);
      key = decryptSaved(saved.get());
    }

    ActiveAiConfig config = new ActiveAiConfig(name, endpoint, model, key);
    long start = System.nanoTime();
    AiResult result = chain.callConfigured(config, TEST_PROMPT, TEST_MAX_TOKENS);
    long latencyMs = (System.nanoTime() - start) / 1_000_000;

    boolean connected = result.isSuccess() || result.failure() == AiFailure.INVALID_RESPONSE;
    String message =
        connected
            ? "Connected to " + displayName(name, endpoint) + " (" + model + ")"
            : AiFallbackChain.describe(result.failure())
                + ": "
                + SecretRedactor.redact(result.detail(), key);
    log.info("AI connection test {}: {}", config.label(), connected ? "ok" : result.failure());
    return AiConnectionTestData.builder()
        .success(connected)
        .name(name)
        .endpoint(endpoint)
        .model(model)
        .latencyMs(latencyMs)
        .message(message)
        .build();
  }

  private String decryptSaved(AiProviderConfigEntity saved) {
    requireEncryption();
    if (saved.getApiKeyCiphertext() == null) {
      throw new IllegalArgumentException("API key is required");
    }
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
            .envChain(chain.envChainSummary());
    if (saved.isEmpty()) {
      return data.source(AiProviderConfigData.Source.ENV)
          .hasKey(!chain.envChainSummary().isEmpty())
          .build();
    }
    AiProviderConfigEntity c = saved.get();
    return data.source(AiProviderConfigData.Source.DATABASE)
        .name(c.getName())
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

  /** The connection's name, or its host when it has none (a test before the first save). */
  private static String displayName(String name, String endpoint) {
    return !isBlank(name) ? name : URI.create(endpoint).getHost();
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

  /** The validated OpenAI-compatible endpoint (https, public host). */
  private String validateEndpoint(String endpoint) {
    if (isBlank(endpoint)) {
      throw new IllegalArgumentException("Endpoint is required");
    }
    return endpointValidator.validate(endpoint);
  }

  private static String requireName(String name) {
    if (isBlank(name)) {
      throw new IllegalArgumentException("Name is required");
    }
    String trimmed = name.trim();
    if (trimmed.length() > MAX_NAME_LENGTH) {
      throw new IllegalArgumentException("Name must be at most " + MAX_NAME_LENGTH + " characters");
    }
    if (trimmed.chars().anyMatch(Character::isISOControl)) {
      throw new IllegalArgumentException("Name must not contain control characters");
    }
    return trimmed;
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
