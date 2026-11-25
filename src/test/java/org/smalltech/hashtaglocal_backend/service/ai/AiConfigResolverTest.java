package org.smalltech.hashtaglocal_backend.service.ai;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.smalltech.hashtaglocal_backend.entity.AiProviderConfigEntity;
import org.smalltech.hashtaglocal_backend.repository.AiProviderConfigRepository;
import org.smalltech.hashtaglocal_backend.security.SecretCipher;

class AiConfigResolverTest {

  private final AiProviderConfigRepository repository = mock(AiProviderConfigRepository.class);
  private final SecretCipher cipher =
      new SecretCipher(Base64.getEncoder().encodeToString(new byte[32]));
  private final AiConfigProperties properties = new AiConfigProperties();
  private Instant now = Instant.parse("2026-11-24T10:00:00Z");
  private final Clock clock =
      new Clock() {
        @Override
        public ZoneId getZone() {
          return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
          return this;
        }

        @Override
        public Instant instant() {
          return now;
        }
      };

  @Test
  void cachesForTheTtlThenRereads() {
    properties.setCacheTtl(Duration.ofSeconds(30));
    AiConfigResolver resolver = new AiConfigResolver(repository, cipher, properties, clock);
    when(repository.findTopByOrderByIdDesc()).thenReturn(Optional.empty());

    resolver.active();
    now = now.plusSeconds(29);
    resolver.active();
    verify(repository, times(1)).findTopByOrderByIdDesc();

    now = now.plusSeconds(2);
    resolver.active();
    verify(repository, times(2)).findTopByOrderByIdDesc();
  }

  @Test
  void invalidateForcesAFreshRead() {
    AiConfigResolver resolver = new AiConfigResolver(repository, cipher, properties, clock);
    when(repository.findTopByOrderByIdDesc())
        .thenReturn(Optional.empty())
        .thenReturn(Optional.of(row("sk-ant-new-key-1234")));

    assertTrue(resolver.active().isEmpty());
    resolver.invalidate();

    AiConfigResolver.ActiveAiConfig config = resolver.active().orElseThrow();
    assertEquals("sk-ant-new-key-1234", config.apiKey());
    assertFalse(config.toString().contains("sk-ant-new-key-1234"), "toString hides the key");
  }

  @Test
  void aRemovedRowAsTheNewestMeansNothingIsSaved() {
    AiConfigResolver resolver = new AiConfigResolver(repository, cipher, properties, clock);
    AiProviderConfigEntity removed =
        AiProviderConfigEntity.builder().id(2L).keyChanged(true).updatedBy(1L).build();
    when(repository.findTopByOrderByIdDesc()).thenReturn(Optional.of(removed));

    assertTrue(resolver.active().isEmpty(), "quiz generation falls back to .env");
  }

  private AiProviderConfigEntity row(String key) {
    return AiProviderConfigEntity.builder()
        .id(1L)
        .name("Claude")
        .endpoint("https://api.anthropic.com/v1/messages")
        .model("claude-x")
        .apiKeyCiphertext(cipher.encrypt(key, AiProviderConfigEntity.KEY_CIPHER_CONTEXT))
        .build();
  }
}
