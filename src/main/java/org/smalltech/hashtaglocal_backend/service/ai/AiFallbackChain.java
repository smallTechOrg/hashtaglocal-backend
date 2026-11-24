package org.smalltech.hashtaglocal_backend.service.ai;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * Sends a quiz prompt to the AI.
 *
 * <p><b>Admin-configured provider.</b> When an admin has saved a provider in the Ops Dashboard
 * ({@link AiConfigResolver}), only that provider/model/key is used, read at request time so a save
 * takes effect on the next call without a restart. A failure there is thrown as an {@link
 * AiProviderException} instead of silently falling back, so the admin sees what is wrong.
 *
 * <p><b>.env chain (fallback).</b> With no saved config, the prompt goes down the configured
 * providers until one answers. Order: the best model on every key, then the next model, then the
 * next provider.
 *
 * <p>A slot that fails is put on cooldown, so later calls skip it without trying: seconds for a
 * rate limit, an hour for used-up quota, until restart for a bad key or missing model. A key-level
 * failure (bad key, no credit) skips every model on that key. Cooldowns live in memory and reset on
 * restart.
 *
 * <p>A BAD_REQUEST means the request itself was rejected, so the rest of that provider is skipped
 * for this call (without a cooldown) and the next provider is tried.
 */
@Component
@Slf4j
public class AiFallbackChain {

  static final Duration DEFAULT_RATE_LIMIT_COOLDOWN = Duration.ofSeconds(30);
  static final Duration DEFAULT_QUOTA_COOLDOWN = Duration.ofHours(1);
  static final Duration TRANSIENT_COOLDOWN = Duration.ofMinutes(1);

  /** The reply text and which provider/model wrote it, e.g. "groq/llama-x". */
  public record AiCompletion(String text, String generatedBy) {}

  /** One provider + model + key. Labels use the key's position, never the key itself. */
  record Slot(AiProvider provider, String model, String apiKey, int keyNumber) {
    String keyLabel() {
      return provider.name() + "#" + keyNumber;
    }

    String label() {
      return keyLabel() + "/" + model;
    }
  }

  private final List<Slot> slots;
  private final Map<String, AiProvider> providersByName;
  private final AiConfigResolver configResolver;
  private final EndpointValidator endpointValidator;
  private final Clock clock;
  private final Map<String, Instant> cooldownUntil = new ConcurrentHashMap<>();

  @Autowired
  public AiFallbackChain(
      QuizAiProperties properties,
      List<AiProvider> providers,
      AiConfigResolver configResolver,
      EndpointValidator endpointValidator) {
    this(properties, providers, configResolver, endpointValidator, Clock.systemUTC());
  }

  /** The .env chain only, with no admin-configured provider. */
  public AiFallbackChain(QuizAiProperties properties, List<AiProvider> providers) {
    this(properties, providers, null, null, Clock.systemUTC());
  }

  AiFallbackChain(QuizAiProperties properties, List<AiProvider> providers, Clock clock) {
    this(properties, providers, null, null, clock);
  }

  AiFallbackChain(
      QuizAiProperties properties,
      List<AiProvider> providers,
      AiConfigResolver configResolver,
      EndpointValidator endpointValidator,
      Clock clock) {
    this.clock = clock;
    this.configResolver = configResolver;
    this.endpointValidator = endpointValidator;
    this.providersByName =
        providers.stream()
            .collect(
                Collectors.toMap(
                    AiProvider::name, Function.identity(), (a, b) -> a, LinkedHashMap::new));
    this.slots = buildSlots(properties, providers);
    log.info(
        "Quiz AI chain: {}",
        slots.isEmpty()
            ? "no providers configured"
            : slots.stream().map(Slot::label).collect(Collectors.joining(" -> ")));
  }

  /**
   * The reply from the admin-configured provider, or else the first successful reply from the .env
   * chain. Empty when the reply had no usable text, or (.env chain) every slot failed or is cooling
   * down.
   *
   * @throws AiProviderException when the admin-configured provider fails
   */
  public Optional<AiCompletion> complete(String prompt, int maxTokens) {
    Optional<AiConfigResolver.ActiveAiConfig> configured =
        configResolver == null ? Optional.empty() : configResolver.active();
    if (configured.isPresent()) {
      return completeWithConfig(configured.get(), prompt, maxTokens);
    }
    return completeWithEnvChain(prompt, maxTokens);
  }

  /** The registered providers by name, in registration order (the allowlist). */
  public Map<String, AiProvider> providers() {
    return providersByName;
  }

  /** The .env chain's provider/model pairs in order, for display. Never includes keys. */
  public List<String> envChainSummary() {
    return slots.stream().map(s -> s.provider().name() + "/" + s.model()).distinct().toList();
  }

  /**
   * One call to {@code config} with no fallback and no cooldown. An empty reply is returned as
   * empty (the caller may retry); every other failure is thrown with a sanitized reason.
   */
  private Optional<AiCompletion> completeWithConfig(
      AiConfigResolver.ActiveAiConfig config, String prompt, int maxTokens) {
    AiResult result = callConfigured(config, prompt, maxTokens);
    String label = config.provider() + "/" + config.model();
    if (result.isSuccess()) {
      log.info("Quiz AI reply from {} (Ops Dashboard config)", label);
      return Optional.of(new AiCompletion(result.text(), label));
    }
    String detail = SecretRedactor.redact(result.detail(), config.apiKey());
    log.warn("Quiz AI {} (Ops Dashboard config) failed: {} ({})", label, result.failure(), detail);
    if (result.failure() == AiFailure.INVALID_RESPONSE) {
      return Optional.empty();
    }
    throw new AiProviderException(describe(result.failure()) + " [" + label + "]: " + detail);
  }

  /**
   * Calls {@code config} after checking the provider is known and the endpoint is allowed. Shared
   * with the Ops Dashboard's "Test connection". Never throws; a blocked endpoint is a BAD_REQUEST.
   */
  public AiResult callConfigured(
      AiConfigResolver.ActiveAiConfig config, String prompt, int maxTokens) {
    AiProvider provider = providersByName.get(config.provider());
    if (provider == null) {
      return AiResult.failure(
          AiFailure.BAD_REQUEST, "unknown provider '" + config.provider() + "'");
    }
    if (endpointValidator != null) {
      try {
        endpointValidator.validate(config.endpoint());
      } catch (IllegalArgumentException e) {
        return AiResult.failure(AiFailure.BAD_REQUEST, e.getMessage());
      }
    }
    return provider.call(config.endpoint(), config.model(), config.apiKey(), prompt, maxTokens);
  }

  /** A short, admin-facing description of a failure type. */
  public static String describe(AiFailure failure) {
    return switch (failure) {
      case RATE_LIMITED -> "AI provider rate limit reached, try again shortly";
      case QUOTA_EXHAUSTED -> "AI provider quota or credit used up";
      case AUTH_FAILED -> "AI provider rejected the API key";
      case MODEL_UNAVAILABLE -> "AI model not found or not available for this key";
      case TRANSIENT -> "AI provider unreachable or failing";
      case INVALID_RESPONSE -> "AI provider reply had no usable text";
      case BAD_REQUEST -> "AI provider rejected the request";
    };
  }

  private Optional<AiCompletion> completeWithEnvChain(String prompt, int maxTokens) {
    Set<String> skippedProviders = new HashSet<>();
    for (Slot slot : slots) {
      if (skippedProviders.contains(slot.provider().name()) || isCoolingDown(slot)) {
        continue;
      }
      AiResult result =
          slot.provider()
              .call(
                  slot.provider().defaultEndpoint(),
                  slot.model(),
                  slot.apiKey(),
                  prompt,
                  maxTokens);
      if (result.isSuccess()) {
        log.info("Quiz AI reply from {}", slot.label());
        return Optional.of(
            new AiCompletion(result.text(), slot.provider().name() + "/" + slot.model()));
      }
      log.warn("Quiz AI {} failed: {} ({})", slot.label(), result.failure(), result.detail());
      if (result.failure() == AiFailure.BAD_REQUEST) {
        skippedProviders.add(slot.provider().name());
      } else {
        coolDown(slot, result);
      }
    }
    log.warn("Quiz AI: every provider failed or is cooling down");
    return Optional.empty();
  }

  private boolean isCoolingDown(Slot slot) {
    Instant now = clock.instant();
    return isBefore(now, cooldownUntil.get(slot.label()))
        || isBefore(now, cooldownUntil.get(slot.keyLabel()));
  }

  private static boolean isBefore(Instant now, Instant until) {
    return until != null && now.isBefore(until);
  }

  private void coolDown(Slot slot, AiResult result) {
    Instant until = cooldownEnd(result);
    if (until == null) {
      return;
    }
    String target = result.wholeKey() ? slot.keyLabel() : slot.label();
    cooldownUntil.put(target, until);
    log.warn(
        "Quiz AI {} cooling down {}",
        target,
        until.equals(Instant.MAX) ? "until restart" : "until " + until);
  }

  /** When the slot may be tried again; null for no cooldown. */
  private Instant cooldownEnd(AiResult result) {
    Instant now = clock.instant();
    return switch (result.failure()) {
      case RATE_LIMITED -> now.plus(orDefault(result.retryAfter(), DEFAULT_RATE_LIMIT_COOLDOWN));
      case QUOTA_EXHAUSTED -> now.plus(orDefault(result.retryAfter(), DEFAULT_QUOTA_COOLDOWN));
      case TRANSIENT -> now.plus(TRANSIENT_COOLDOWN);
      case AUTH_FAILED, MODEL_UNAVAILABLE -> Instant.MAX;
      case INVALID_RESPONSE, BAD_REQUEST -> null;
    };
  }

  private static Duration orDefault(Duration value, Duration fallback) {
    return value != null && !value.isZero() && !value.isNegative() ? value : fallback;
  }

  /** Slots in order: for each level, each model across all keys. Unusable levels are skipped. */
  private static List<Slot> buildSlots(QuizAiProperties properties, List<AiProvider> providers) {
    Map<String, AiProvider> byName =
        providers.stream().collect(Collectors.toMap(AiProvider::name, Function.identity()));
    List<Slot> result = new ArrayList<>();
    for (QuizAiProperties.Level level : properties.getChain()) {
      AiProvider provider = byName.get(level.getProvider());
      if (provider == null) {
        log.error("Quiz AI chain: unknown provider '{}', skipped", level.getProvider());
        continue;
      }
      List<String> models = clean(level.getModels());
      List<String> keys = clean(level.getKeys());
      if (models.isEmpty() || keys.isEmpty()) {
        log.info("Quiz AI chain: {} has no models or no keys, skipped", provider.name());
        continue;
      }
      for (String model : models) {
        for (int i = 0; i < keys.size(); i++) {
          result.add(new Slot(provider, model, keys.get(i), i + 1));
        }
      }
    }
    return List.copyOf(result);
  }

  private static List<String> clean(List<String> values) {
    return values == null
        ? List.of()
        : values.stream().map(String::trim).filter(v -> !v.isEmpty()).distinct().toList();
  }
}
