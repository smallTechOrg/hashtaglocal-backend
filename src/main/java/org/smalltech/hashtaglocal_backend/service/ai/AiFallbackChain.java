package org.smalltech.hashtaglocal_backend.service.ai;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
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
 * Sends a prompt down the configured providers until one answers. Order: the best model on every
 * key, then the next model, then the next provider.
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
  private final Clock clock;
  private final Map<String, Instant> cooldownUntil = new ConcurrentHashMap<>();

  @Autowired
  public AiFallbackChain(QuizAiProperties properties, List<AiProvider> providers) {
    this(properties, providers, Clock.systemUTC());
  }

  AiFallbackChain(QuizAiProperties properties, List<AiProvider> providers, Clock clock) {
    this.clock = clock;
    this.slots = buildSlots(properties, providers);
    log.info(
        "Quiz AI chain: {}",
        slots.isEmpty()
            ? "no providers configured"
            : slots.stream().map(Slot::label).collect(Collectors.joining(" -> ")));
  }

  /** The first successful reply, or empty when every slot failed or is cooling down. */
  public Optional<AiCompletion> complete(String prompt, int maxTokens) {
    Set<String> skippedProviders = new HashSet<>();
    for (Slot slot : slots) {
      if (skippedProviders.contains(slot.provider().name()) || isCoolingDown(slot)) {
        continue;
      }
      AiResult result = slot.provider().call(slot.model(), slot.apiKey(), prompt, maxTokens);
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
