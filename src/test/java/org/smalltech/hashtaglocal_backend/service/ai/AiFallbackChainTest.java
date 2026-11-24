package org.smalltech.hashtaglocal_backend.service.ai;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.smalltech.hashtaglocal_backend.service.ai.AiFallbackChain.AiCompletion;

class AiFallbackChainTest {

  private final MutableClock clock = new MutableClock(Instant.parse("2026-11-20T10:00:00Z"));
  private final List<String> calls = new ArrayList<>();
  private final FakeProvider claude = new FakeProvider("anthropic");
  private final FakeProvider groq = new FakeProvider("groq");
  private final FakeProvider gemini = new FakeProvider("gemini");

  @Test
  void triesBestModelOnEveryKeyThenNextModelThenNextProvider() {
    AiFallbackChain chain =
        chain(
            level("anthropic", "sonnet,haiku", "c1,c2"),
            level("groq", "llama", "g1"),
            level("gemini", "flash", "m1"));
    claude.failAlways(AiResult.failure(AiFailure.TRANSIENT, "down"));
    groq.failAlways(AiResult.failure(AiFailure.TRANSIENT, "down"));
    gemini.answer("m1", "flash", "quiz text");

    Optional<AiCompletion> reply = chain.complete("prompt", 100);

    assertEquals(
        List.of(
            "anthropic:c1:sonnet",
            "anthropic:c2:sonnet",
            "anthropic:c1:haiku",
            "anthropic:c2:haiku",
            "groq:g1:llama",
            "gemini:m1:flash"),
        calls);
    assertEquals("quiz text", reply.orElseThrow().text());
    assertEquals("gemini/flash", reply.orElseThrow().generatedBy());
  }

  @Test
  void stopsAtTheFirstSuccess() {
    AiFallbackChain chain = chain(level("anthropic", "sonnet", "c1,c2"), level("groq", "x", "g1"));
    claude.answer("c1", "sonnet", "from claude");

    assertEquals("anthropic/sonnet", chain.complete("p", 100).orElseThrow().generatedBy());
    assertEquals(List.of("anthropic:c1:sonnet"), calls);
  }

  @Test
  void exhaustedSlotIsSkippedUntilItsCooldownEnds() {
    AiFallbackChain chain = chain(level("groq", "llama", "g1,g2"));
    groq.fail("g1", "llama", AiResult.failure(AiFailure.QUOTA_EXHAUSTED, Duration.ofHours(2), ""));
    groq.answer("g2", "llama", "ok");

    chain.complete("p", 100);
    calls.clear();
    chain.complete("p", 100);
    assertEquals(List.of("groq:g2:llama"), calls, "g1 is cooling down, so it is not called");

    clock.advance(Duration.ofHours(2).plusSeconds(1));
    calls.clear();
    groq.answer("g1", "llama", "back");
    assertEquals("back", chain.complete("p", 100).orElseThrow().text());
    assertEquals(List.of("groq:g1:llama"), calls);
  }

  @Test
  void quotaWithoutRetryAfterUsesTheDefaultCooldown() {
    AiFallbackChain chain = chain(level("groq", "llama", "g1"));
    groq.fail("g1", "llama", AiResult.failure(AiFailure.QUOTA_EXHAUSTED, ""));
    chain.complete("p", 100);

    clock.advance(AiFallbackChain.DEFAULT_QUOTA_COOLDOWN.minusSeconds(1));
    calls.clear();
    chain.complete("p", 100);
    assertTrue(calls.isEmpty());

    clock.advance(Duration.ofSeconds(2));
    chain.complete("p", 100);
    assertEquals(List.of("groq:g1:llama"), calls);
  }

  @Test
  void keyFailureSkipsEveryModelOnThatKey() {
    AiFallbackChain chain = chain(level("anthropic", "sonnet,haiku", "c1,c2"));
    claude.fail("c1", "sonnet", AiResult.keyFailure(AiFailure.QUOTA_EXHAUSTED, "no credit"));
    claude.failAlways(AiResult.failure(AiFailure.TRANSIENT, "down"));

    chain.complete("p", 100);

    // c1 ran out of credit on sonnet, so it is not tried for haiku either.
    assertEquals(
        List.of("anthropic:c1:sonnet", "anthropic:c2:sonnet", "anthropic:c2:haiku"), calls);
  }

  @Test
  void badKeyIsSkippedUntilRestart() {
    AiFallbackChain chain = chain(level("gemini", "flash", "m1,m2"));
    gemini.fail("m1", "flash", AiResult.keyFailure(AiFailure.AUTH_FAILED, "bad key"));
    gemini.answer("m2", "flash", "ok");
    chain.complete("p", 100);

    clock.advance(Duration.ofDays(30));
    calls.clear();
    chain.complete("p", 100);
    assertEquals(List.of("gemini:m2:flash"), calls);
  }

  @Test
  void badRequestSkipsRestOfThatProviderWithoutCooldown() {
    AiFallbackChain chain =
        chain(level("anthropic", "sonnet,haiku", "c1,c2"), level("groq", "llama", "g1"));
    claude.fail("c1", "sonnet", AiResult.failure(AiFailure.BAD_REQUEST, "400"));
    groq.answer("g1", "llama", "ok");

    assertEquals("groq/llama", chain.complete("p", 100).orElseThrow().generatedBy());
    assertEquals(List.of("anthropic:c1:sonnet", "groq:g1:llama"), calls);

    // No cooldown: the next call tries Claude again.
    calls.clear();
    claude.answer("c1", "sonnet", "fixed");
    assertEquals("anthropic/sonnet", chain.complete("p", 100).orElseThrow().generatedBy());
  }

  @Test
  void emptyReplyMovesOnWithoutCooldown() {
    AiFallbackChain chain = chain(level("groq", "llama", "g1,g2"));
    groq.fail("g1", "llama", AiResult.failure(AiFailure.INVALID_RESPONSE, "empty"));
    groq.answer("g2", "llama", "ok");
    chain.complete("p", 100);

    calls.clear();
    chain.complete("p", 100);
    assertEquals(List.of("groq:g1:llama", "groq:g2:llama"), calls);
  }

  @Test
  void everythingFailingReturnsEmpty() {
    AiFallbackChain chain = chain(level("anthropic", "sonnet", "c1"), level("groq", "llama", "g1"));
    claude.failAlways(AiResult.failure(AiFailure.TRANSIENT, "down"));
    groq.failAlways(AiResult.failure(AiFailure.RATE_LIMITED, "429"));

    assertFalse(chain.complete("p", 100).isPresent());
  }

  @Test
  void levelsWithoutKeysOrModelsOrUnknownProvidersAreSkipped() {
    AiFallbackChain chain =
        chain(
            level("anthropic", "sonnet", " , "),
            level("groq", "", "g1"),
            level("openai", "gpt", "o1"),
            level("gemini", "flash", "m1"));
    gemini.answer("m1", "flash", "ok");

    assertEquals("gemini/flash", chain.complete("p", 100).orElseThrow().generatedBy());
    assertEquals(List.of("gemini:m1:flash"), calls);
  }

  private AiFallbackChain chain(QuizAiProperties.Level... levels) {
    QuizAiProperties properties = new QuizAiProperties();
    properties.setChain(List.of(levels));
    return new AiFallbackChain(properties, List.of(claude, groq, gemini), clock);
  }

  private static QuizAiProperties.Level level(String provider, String models, String keys) {
    QuizAiProperties.Level level = new QuizAiProperties.Level();
    level.setProvider(provider);
    level.setModels(List.of(models.split(",", -1)));
    level.setKeys(List.of(keys.split(",", -1)));
    return level;
  }

  /** Records every call; each key+model answers with its latest script, else the default. */
  private class FakeProvider implements AiProvider {
    private final String name;
    private final Map<String, AiResult> scripted = new HashMap<>();
    private AiResult otherwise = AiResult.failure(AiFailure.TRANSIENT, "not scripted");

    FakeProvider(String name) {
      this.name = name;
    }

    void answer(String key, String model, String text) {
      scripted.put(key + ":" + model, AiResult.success(text));
    }

    void fail(String key, String model, AiResult result) {
      scripted.put(key + ":" + model, result);
    }

    void failAlways(AiResult result) {
      otherwise = result;
    }

    @Override
    public String name() {
      return name;
    }

    @Override
    public String defaultEndpoint() {
      return "https://" + name + ".example.com/v1";
    }

    @Override
    public AiResult call(
        String endpoint, String model, String apiKey, String prompt, int maxTokens) {
      calls.add(name + ":" + apiKey + ":" + model);
      return scripted.getOrDefault(apiKey + ":" + model, otherwise);
    }
  }

  private static class MutableClock extends Clock {
    private Instant now;

    MutableClock(Instant start) {
      this.now = start;
    }

    void advance(Duration duration) {
      now = now.plus(duration);
    }

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
  }
}
