package org.smalltech.hashtaglocal_backend.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.smalltech.hashtaglocal_backend.entity.AiProviderConfigEntity;
import org.smalltech.hashtaglocal_backend.entity.Locality;
import org.smalltech.hashtaglocal_backend.exception.DownstreamServiceException;
import org.smalltech.hashtaglocal_backend.model.request.AiProviderConfigRequest;
import org.smalltech.hashtaglocal_backend.model.response.AiConnectionTestData;
import org.smalltech.hashtaglocal_backend.model.response.AiProviderConfigData;
import org.smalltech.hashtaglocal_backend.repository.AiProviderConfigRepository;
import org.smalltech.hashtaglocal_backend.repository.QuizRepository;
import org.smalltech.hashtaglocal_backend.repository.UserRepository;
import org.smalltech.hashtaglocal_backend.security.SecretCipher;
import org.smalltech.hashtaglocal_backend.service.ai.AiConfigProperties;
import org.smalltech.hashtaglocal_backend.service.ai.AiConfigResolver;
import org.smalltech.hashtaglocal_backend.service.ai.AiFailure;
import org.smalltech.hashtaglocal_backend.service.ai.AiFallbackChain;
import org.smalltech.hashtaglocal_backend.service.ai.AiProvider;
import org.smalltech.hashtaglocal_backend.service.ai.AiProviderException;
import org.smalltech.hashtaglocal_backend.service.ai.AiResult;
import org.smalltech.hashtaglocal_backend.service.ai.EndpointValidator;
import org.smalltech.hashtaglocal_backend.service.ai.OpenAiCompatibleClient;
import org.smalltech.hashtaglocal_backend.service.ai.QuizAiProperties;
import org.springframework.web.client.RestClient;

/**
 * The admin AI connection end to end, with the real cipher, resolver, chain and quiz generation, a
 * fake OpenAI-compatible server, and an in-memory stand-in for the ai_configs table (rows are only
 * added).
 */
class AiProviderConfigServiceTest {

  private static final Long ADMIN = 7L;
  private static final String CLAUDE = "https://api.anthropic.example.com/v1";
  private static final String OPENAI = "https://api.openai.example.com/v1";
  private static final String CLAUDE_KEY = "sk-ant-api03-first-secret-AAAA";
  private static final String OPENAI_KEY = "sk-proj-second-secret-BBBB";
  private static final String ENV_KEY = "env-groq-key-123";

  private final ObjectMapper json =
      new ObjectMapper()
          .findAndRegisterModules()
          .setPropertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE);

  /** The ai_configs table, oldest row first. */
  private final List<AiProviderConfigEntity> rows = new ArrayList<>();

  /** Every AI call made, as "endpoint:model:key" (connection) or "groq:model:key" (.env). */
  private final List<String> calls = new ArrayList<>();

  private AiProviderConfigRepository repository;
  private SecretCipher cipher;
  private AiConfigResolver resolver;
  private AiFallbackChain chain;
  private AiProviderConfigService service;
  private FakeServer server;

  @BeforeEach
  void setUp() {
    repository = mock(AiProviderConfigRepository.class);
    // Hand out copies, as the database would, so nothing can change a cached row behind our back.
    when(repository.findTopByOrderByIdDesc())
        .thenAnswer(inv -> Optional.ofNullable(latest()).map(AiProviderConfigServiceTest::copy));
    when(repository.save(any()))
        .thenAnswer(
            inv -> {
              AiProviderConfigEntity added = copy(inv.getArgument(0));
              assertNull(added.getId(), "rows are only ever added, never updated");
              added.setId((long) rows.size() + 1);
              assertNull(added.getCreatedAt(), "createdAt is left to Hibernate");
              assertNull(added.getUpdatedAt(), "updatedAt is left to Hibernate");
              // What @CreationTimestamp / @UpdateTimestamp do on insert.
              LocalDateTime now = LocalDateTime.now();
              added.setCreatedAt(now);
              added.setUpdatedAt(now);
              rows.add(added);
              return copy(added);
            });
    when(repository.clearKeysExcept(anyLong()))
        .thenAnswer(
            inv -> {
              long keep = inv.getArgument(0);
              int cleared = 0;
              for (AiProviderConfigEntity r : rows) {
                if (r.getId() != keep && r.getApiKeyCiphertext() != null) {
                  r.setApiKeyCiphertext(null);
                  r.setUpdatedAt(LocalDateTime.now());
                  cleared++;
                }
              }
              return cleared;
            });

    cipher = new SecretCipher(randomKey());
    AiConfigProperties properties = new AiConfigProperties();
    // The fake endpoints below are not resolvable; DNS checks are covered by EndpointValidatorTest.
    properties.setAllowPrivateEndpoints(true);
    resolver = new AiConfigResolver(repository, cipher, properties);
    server = new FakeServer();

    QuizAiProperties env = new QuizAiProperties();
    QuizAiProperties.Level level = new QuizAiProperties.Level();
    level.setProvider("groq");
    level.setModels(List.of("llama-env"));
    level.setKeys(List.of(ENV_KEY));
    env.setChain(List.of(level));
    chain =
        new AiFallbackChain(
            env, List.of(new EnvGroq()), resolver, new EndpointValidator(properties), server);

    UserRepository users = mock(UserRepository.class);
    when(users.findById(anyLong())).thenReturn(Optional.empty());
    service =
        new AiProviderConfigService(
            repository, users, cipher, new EndpointValidator(properties), chain, resolver);
  }

  // ---- .env fallback ----

  @Test
  void withNothingSavedQuizGenerationUsesTheEnvChain() {
    assertEquals("groq/llama-env", generateQuiz());
    // One call to generate, one to evaluate, both on the .env chain.
    assertEquals(List.of("groq:llama-env:" + ENV_KEY, "groq:llama-env:" + ENV_KEY), calls);

    AiProviderConfigData data = service.get();
    assertEquals(AiProviderConfigData.Source.ENV, data.getSource());
    assertEquals(List.of("groq/llama-env"), data.getEnvChain());
  }

  // ---- save, encryption, never returning the key ----

  @Test
  void saveStoresTheNameAndOnlyCiphertextAndReturnsNoKey() throws Exception {
    AiProviderConfigData data =
        service.save(request("Claude", CLAUDE, "claude-x", CLAUDE_KEY), ADMIN);

    AiProviderConfigEntity stored = latest();
    assertEquals("Claude", stored.getName());
    assertEquals(CLAUDE, stored.getEndpoint());
    assertFalse(stored.getApiKeyCiphertext().contains(CLAUDE_KEY));
    assertEquals(
        CLAUDE_KEY,
        cipher.decrypt(stored.getApiKeyCiphertext(), AiProviderConfigEntity.KEY_CIPHER_CONTEXT));
    assertEquals(ADMIN, stored.getUpdatedBy());
    assertNotNull(stored.getCreatedAt());
    assertNotNull(stored.getUpdatedAt());

    assertEquals("Claude", data.getName());
    assertTrue(data.isHasKey());
    assertEquals("AAAA", data.getKeyLast4());
    assertKeyAbsent(json.writeValueAsString(data), stored);
    assertKeyAbsent(json.writeValueAsString(service.get()), stored);
  }

  @Test
  void blankKeyOnUpdateKeepsTheSavedKey() {
    service.save(request("Claude", CLAUDE, "claude-x", CLAUDE_KEY), ADMIN);
    String ciphertextBefore = latest().getApiKeyCiphertext();

    service.save(request("Claude (v2 path)", CLAUDE + "/beta", "claude-y", "  "), ADMIN);

    assertEquals(ciphertextBefore, latest().getApiKeyCiphertext());
    assertEquals("Claude (v2 path)", latest().getName());
    assertEquals("claude-y", latest().getModel());
    assertFalse(latest().isKeyChanged());
  }

  @Test
  void savedKeyIsNeverSentToADifferentServer() {
    service.save(request("Claude", CLAUDE, "claude-x", CLAUDE_KEY), ADMIN);

    IllegalArgumentException onSave =
        assertThrows(
            IllegalArgumentException.class,
            () ->
                service.save(
                    request("Claude", "https://evil.example.com/v1", "claude-x", ""), ADMIN));
    assertTrue(onSave.getMessage().contains("Enter the API key again"), onSave.getMessage());
    assertEquals(1, rows.size(), "refused save adds nothing");

    calls.clear();
    assertThrows(
        IllegalArgumentException.class,
        () -> service.test(request("", "https://evil.example.com/v1", "", "")));
    assertTrue(calls.isEmpty(), "no call is made with the saved key to the new host");

    // Same host on another port is a different server too.
    assertThrows(
        IllegalArgumentException.class,
        () -> service.test(request("", "https://api.anthropic.example.com:8443/v1", "", "")));
  }

  @Test
  void newHostWorksWhenTheKeyIsEnteredAgain() {
    service.save(request("Claude", CLAUDE, "claude-x", CLAUDE_KEY), ADMIN);

    service.save(request("ChatGPT", OPENAI, "gpt-x", OPENAI_KEY), ADMIN);

    assertEquals(OPENAI, latest().getEndpoint());
    assertEquals("BBBB", latest().getApiKeyLast4());
  }

  @Test
  void originIgnoresCaseAndDefaultPort() {
    assertEquals(
        AiProviderConfigService.origin("https://API.Example.com/v1"),
        AiProviderConfigService.origin("https://api.example.com:443/v2/other"));
    assertNotEquals(
        AiProviderConfigService.origin("https://api.example.com/v1"),
        AiProviderConfigService.origin("https://api.example.com.evil.io/v1"));
  }

  @Test
  void firstSaveRequiresAKey() {
    assertThrows(
        IllegalArgumentException.class,
        () -> service.save(request("Claude", CLAUDE, "claude-x", null), ADMIN));
    assertTrue(rows.isEmpty());
  }

  @Test
  void rejectsMissingNameEndpointOrModel() {
    assertThrows(
        IllegalArgumentException.class,
        () -> service.save(request(" ", CLAUDE, "m", CLAUDE_KEY), ADMIN));
    assertThrows(
        IllegalArgumentException.class,
        () -> service.save(request("x".repeat(101), CLAUDE, "m", CLAUDE_KEY), ADMIN));
    assertThrows(
        IllegalArgumentException.class,
        () -> service.save(request("Claude", " ", "m", CLAUDE_KEY), ADMIN));
    assertThrows(
        IllegalArgumentException.class,
        () -> service.save(request("Claude", "http://api.example.com/v1", "m", CLAUDE_KEY), ADMIN));
    assertThrows(
        IllegalArgumentException.class,
        () -> service.save(request("Claude", CLAUDE, "  ", CLAUDE_KEY), ADMIN));
    assertThrows(
        IllegalArgumentException.class,
        () -> service.save(request("Claude", CLAUDE, "bad model", CLAUDE_KEY), ADMIN));
    assertTrue(rows.isEmpty());
    verify(repository, never()).save(any());
  }

  @Test
  void refusesToStoreAKeyWithoutAnEncryptionKey() {
    service =
        new AiProviderConfigService(
            repository,
            mock(UserRepository.class),
            new SecretCipher(""),
            new EndpointValidator(permissive()),
            chain,
            resolver);

    DownstreamServiceException e =
        assertThrows(
            DownstreamServiceException.class,
            () -> service.save(request("Claude", CLAUDE, "claude-x", CLAUDE_KEY), ADMIN));
    assertEquals("ENCRYPTION_NOT_CONFIGURED", e.getType());
    assertTrue(rows.isEmpty());
  }

  // ---- one table: current connection + history ----

  @Test
  void everyChangeIsARowButOnlyTheNewestKeepsAKey() {
    service.save(request("Claude", CLAUDE, "claude-x", CLAUDE_KEY), ADMIN);
    service.save(request("Claude", CLAUDE, "claude-y", ""), 8L); // model only, key kept
    service.save(request("ChatGPT", OPENAI, "gpt-x", OPENAI_KEY), ADMIN); // new server and key

    assertEquals(3, rows.size());
    assertEquals(
        List.of("Claude", "Claude", "ChatGPT"),
        rows.stream().map(AiProviderConfigEntity::getName).toList());
    assertEquals(
        List.of("claude-x", "claude-y", "gpt-x"),
        rows.stream().map(AiProviderConfigEntity::getModel).toList());
    assertEquals(
        List.of(true, false, true),
        rows.stream().map(AiProviderConfigEntity::isKeyChanged).toList());
    assertEquals(
        List.of("AAAA", "AAAA", "BBBB"),
        rows.stream().map(AiProviderConfigEntity::getApiKeyLast4).toList());
    assertEquals(8L, rows.get(1).getUpdatedBy());
    assertNull(rows.get(0).getApiKeyCiphertext(), "older rows lose their key");
    assertNull(rows.get(1).getApiKeyCiphertext(), "older rows lose their key");
    assertEquals(
        OPENAI_KEY,
        cipher.decrypt(
            rows.get(2).getApiKeyCiphertext(), AiProviderConfigEntity.KEY_CIPHER_CONTEXT));
    assertEquals("ChatGPT", service.get().getName(), "the newest row is in effect");
  }

  @Test
  void removeKeyAddsARowAndLeavesNoKeyInTheDatabase() {
    service.save(request("Claude", CLAUDE, "claude-x", CLAUDE_KEY), ADMIN);

    service.reset(9L);

    assertEquals(2, rows.size());
    AiProviderConfigEntity removed = latest();
    assertNull(removed.getName());
    assertNull(removed.getEndpoint());
    assertNull(removed.getModel());
    assertTrue(removed.isKeyChanged());
    assertEquals(9L, removed.getUpdatedBy());
    assertNotNull(removed.getCreatedAt());
    assertNotNull(removed.getUpdatedAt());
    assertTrue(rows.stream().allMatch(r -> r.getApiKeyCiphertext() == null));
    assertEquals(AiProviderConfigData.Source.ENV, service.get().getSource());

    // The old key is gone, so saving again needs a key.
    assertThrows(
        IllegalArgumentException.class,
        () -> service.save(request("Claude", CLAUDE, "claude-x", ""), ADMIN));
    // Removing again when nothing is saved adds nothing.
    service.reset(ADMIN);
    assertEquals(2, rows.size());
  }

  // ---- runtime switching, no restart ----

  @Test
  void savedConnectionTakesEffectOnTheNextQuizWithoutARestart() {
    assertEquals("groq/llama-env", generateQuiz());

    service.save(request("Claude", CLAUDE, "claude-x", CLAUDE_KEY), ADMIN);
    calls.clear();
    assertEquals("Claude/claude-x", generateQuiz());
    assertTrue(
        calls.stream().allMatch(c -> c.equals(CLAUDE + ":claude-x:" + CLAUDE_KEY)),
        calls.toString());

    service.save(request("ChatGPT", OPENAI, "gpt-x", OPENAI_KEY), ADMIN);
    calls.clear();
    assertEquals("ChatGPT/gpt-x", generateQuiz());
    assertTrue(
        calls.stream().allMatch(c -> c.equals(OPENAI + ":gpt-x:" + OPENAI_KEY)), calls.toString());

    service.reset(ADMIN);
    calls.clear();
    assertEquals("groq/llama-env", generateQuiz());
  }

  @Test
  void failingSavedConnectionIsAClearErrorNotASilentFallback() {
    service.save(request("Claude", CLAUDE, "claude-x", CLAUDE_KEY), ADMIN);
    server.next =
        AiResult.keyFailure(
            AiFailure.AUTH_FAILED,
            "401 invalid x-api-key " + CLAUDE_KEY + " sk-ant-echoed-1234567");

    AiProviderException e = assertThrows(AiProviderException.class, this::generateQuiz);

    assertTrue(e.getMessage().contains("rejected the API key"), e.getMessage());
    assertTrue(e.getMessage().contains("Claude/claude-x"), e.getMessage());
    assertFalse(e.getMessage().contains(CLAUDE_KEY));
    assertFalse(e.getMessage().contains("sk-ant-echoed"));
    assertTrue(calls.stream().noneMatch(c -> c.startsWith("groq:")), "no fallback to .env");
    assertEquals(1, rows.size(), "a failing call never touches the config");
  }

  @Test
  void undecryptableSavedKeyIsAClearError() {
    service.save(request("Claude", CLAUDE, "claude-x", CLAUDE_KEY), ADMIN);
    // Same row, read by a server with a different master key.
    AiConfigResolver otherServer =
        new AiConfigResolver(repository, new SecretCipher(randomKey()), new AiConfigProperties());

    assertThrows(AiProviderException.class, otherServer::active);
  }

  // ---- test ----

  @Test
  void testUsesSubmittedValuesAndSavesNothing() {
    AiConnectionTestData result = service.test(request("ChatGPT", OPENAI, "gpt-x", OPENAI_KEY));

    assertTrue(result.isSuccess());
    assertEquals("Connected to ChatGPT (gpt-x)", result.getMessage());
    assertEquals(List.of(OPENAI + ":gpt-x:" + OPENAI_KEY), calls);
    assertTrue(rows.isEmpty());
  }

  @Test
  void testFallsBackToTheSavedConnection() {
    service.save(request("Claude", CLAUDE, "claude-x", CLAUDE_KEY), ADMIN);

    AiConnectionTestData result = service.test(request("", "", "", ""));

    assertTrue(result.isSuccess());
    assertEquals("Claude", result.getName());
    assertEquals(List.of(CLAUDE + ":claude-x:" + CLAUDE_KEY), calls);
  }

  @Test
  void testReturnsASanitizedError() {
    server.next = AiResult.keyFailure(AiFailure.AUTH_FAILED, "401 Incorrect API key " + OPENAI_KEY);

    AiConnectionTestData result = service.test(request("ChatGPT", OPENAI, "gpt-x", OPENAI_KEY));

    assertFalse(result.isSuccess());
    assertFalse(result.getMessage().contains(OPENAI_KEY));
    assertTrue(result.getMessage().contains("rejected the API key"));
  }

  // ---- helpers ----

  /** The newest row, or null when the table is empty. */
  private AiProviderConfigEntity latest() {
    return rows.isEmpty() ? null : rows.get(rows.size() - 1);
  }

  /** Runs real quiz generation and returns who wrote the quiz (its generated_by). */
  private String generateQuiz() {
    QuizGenerationService quizzes =
        new QuizGenerationService(chain, mock(QuizRepository.class), json);
    Locality place = Locality.builder().id(1L).hashtag("indiranagar").name("Indiranagar").build();
    return quizzes.generateCandidate(place, List.of()).orElseThrow().generatedBy();
  }

  private static AiConfigProperties permissive() {
    AiConfigProperties p = new AiConfigProperties();
    p.setAllowPrivateEndpoints(true);
    return p;
  }

  private static void assertKeyAbsent(String body, AiProviderConfigEntity stored) {
    assertFalse(body.contains(CLAUDE_KEY), body);
    assertFalse(body.contains(stored.getApiKeyCiphertext()), body);
    assertFalse(body.contains("ciphertext"), body);
    assertFalse(body.contains("\"api_key\""), body);
  }

  private static AiProviderConfigRequest request(
      String name, String endpoint, String model, String apiKey) {
    AiProviderConfigRequest r = new AiProviderConfigRequest();
    r.setName(name);
    r.setEndpoint(endpoint);
    r.setModel(model);
    r.setApiKey(apiKey);
    return r;
  }

  private static AiProviderConfigEntity copy(AiProviderConfigEntity e) {
    return AiProviderConfigEntity.builder()
        .id(e.getId())
        .name(e.getName())
        .endpoint(e.getEndpoint())
        .model(e.getModel())
        .apiKeyCiphertext(e.getApiKeyCiphertext())
        .apiKeyLast4(e.getApiKeyLast4())
        .keyChanged(e.isKeyChanged())
        .updatedBy(e.getUpdatedBy())
        .createdAt(e.getCreatedAt())
        .updatedAt(e.getUpdatedAt())
        .build();
  }

  private static String randomKey() {
    byte[] key = new byte[32];
    new SecureRandom().nextBytes(key);
    return Base64.getEncoder().encodeToString(key);
  }

  private static AiResult answer(String prompt) {
    if (prompt.contains("quiz evaluator")) {
      return AiResult.success("{\"is_evaluation_passed\": true, \"rejection_reason\": null}");
    }
    return AiResult.success(
        "{\"question\": \"Which road is famous here?\", \"options\": [\"100 Feet Road\","
            + " \"MG Road\", \"Brigade Road\", \"Church Street\"], \"answer_index\": 1,"
            + " \"explanation\": \"It is lined with cafes.\"}");
  }

  /** The saved connection's OpenAI-compatible server: answers quiz prompts, or {@link #next}. */
  private class FakeServer extends OpenAiCompatibleClient {
    AiResult next;

    FakeServer() {
      super(RestClient.builder(), json);
    }

    @Override
    public AiResult call(String endpoint, String model, String apiKey, String prompt, int max) {
      calls.add(endpoint + ":" + model + ":" + apiKey);
      return next != null ? next : answer(prompt);
    }
  }

  /** The .env chain's Groq provider. */
  private class EnvGroq implements AiProvider {
    @Override
    public String name() {
      return "groq";
    }

    @Override
    public AiResult call(String model, String apiKey, String prompt, int maxTokens) {
      calls.add("groq:" + model + ":" + apiKey);
      return answer(prompt);
    }
  }
}
