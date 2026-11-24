package org.smalltech.hashtaglocal_backend.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
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
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.smalltech.hashtaglocal_backend.entity.AiProviderConfigAuditEntity;
import org.smalltech.hashtaglocal_backend.entity.AiProviderConfigEntity;
import org.smalltech.hashtaglocal_backend.entity.Locality;
import org.smalltech.hashtaglocal_backend.exception.DownstreamServiceException;
import org.smalltech.hashtaglocal_backend.model.request.AiProviderConfigRequest;
import org.smalltech.hashtaglocal_backend.model.response.AiConnectionTestData;
import org.smalltech.hashtaglocal_backend.model.response.AiProviderConfigData;
import org.smalltech.hashtaglocal_backend.repository.AiProviderConfigAuditRepository;
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
import org.smalltech.hashtaglocal_backend.service.ai.QuizAiProperties;

/**
 * The admin AI provider config end to end, with the real cipher, resolver, chain and quiz
 * generation, and an in-memory stand-in for the two tables.
 */
class AiProviderConfigServiceTest {

  private static final Long ADMIN = 7L;
  private static final String CLAUDE_KEY = "sk-ant-api03-first-secret-AAAA";
  private static final String OPENAI_KEY = "sk-proj-second-secret-BBBB";
  private static final String ENV_KEY = "env-groq-key-123";

  private final ObjectMapper json =
      new ObjectMapper()
          .findAndRegisterModules()
          .setPropertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE);

  private final AtomicReference<AiProviderConfigEntity> row = new AtomicReference<>();
  private final List<AiProviderConfigAuditEntity> audits = new ArrayList<>();
  private final List<String> calls = new ArrayList<>();

  private AiProviderConfigRepository repository;
  private AiProviderConfigAuditRepository auditRepository;
  private SecretCipher cipher;
  private AiConfigResolver resolver;
  private AiFallbackChain chain;
  private AiProviderConfigService service;
  private FakeProvider anthropic;
  private FakeProvider openai;
  private FakeProvider groq;

  @BeforeEach
  void setUp() {
    repository = mock(AiProviderConfigRepository.class);
    auditRepository = mock(AiProviderConfigAuditRepository.class);
    // Hand out copies, as the database would, so nothing can change a cached row behind our back.
    when(repository.findById(AiProviderConfigEntity.SINGLETON_ID))
        .thenAnswer(inv -> Optional.ofNullable(row.get()).map(AiProviderConfigServiceTest::copy));
    when(repository.save(any()))
        .thenAnswer(
            inv -> {
              AiProviderConfigEntity saved = copy(inv.getArgument(0));
              row.set(saved);
              return copy(saved);
            });
    org.mockito.Mockito.doAnswer(
            inv -> {
              row.set(null);
              return null;
            })
        .when(repository)
        .delete(any());
    when(auditRepository.save(any()))
        .thenAnswer(
            inv -> {
              audits.add(inv.getArgument(0));
              return inv.getArgument(0);
            });

    cipher = new SecretCipher(randomKey());
    AiConfigProperties properties = new AiConfigProperties();
    // The fake endpoints below are not resolvable; DNS checks are covered by EndpointValidatorTest.
    properties.setAllowPrivateEndpoints(true);
    resolver = new AiConfigResolver(repository, cipher, properties);

    anthropic = new FakeProvider("anthropic");
    openai = new FakeProvider("openai");
    groq = new FakeProvider("groq");
    QuizAiProperties env = new QuizAiProperties();
    QuizAiProperties.Level level = new QuizAiProperties.Level();
    level.setProvider("groq");
    level.setModels(List.of("llama-env"));
    level.setKeys(List.of(ENV_KEY));
    env.setChain(List.of(level));
    chain =
        new AiFallbackChain(
            env,
            List.of(
                anthropic,
                openai,
                groq,
                new org.smalltech.hashtaglocal_backend.service.ai.OpenAiCompatibleProvider(
                    org.springframework.web.client.RestClient.builder(), json)),
            resolver,
            new EndpointValidator(properties));

    UserRepository users = mock(UserRepository.class);
    when(users.findById(anyLong())).thenReturn(Optional.empty());
    service =
        new AiProviderConfigService(
            repository,
            auditRepository,
            users,
            cipher,
            new EndpointValidator(properties),
            chain,
            resolver);
  }

  // ---- .env fallback ----

  @Test
  void withNothingSavedQuizGenerationUsesTheEnvChain() {
    assertEquals("groq/llama-env", generateQuiz());
    // One call to generate, one to evaluate, both on the .env chain.
    String envCall = "groq:" + groq.defaultEndpoint() + ":llama-env:" + ENV_KEY;
    assertEquals(List.of(envCall, envCall), calls);

    AiProviderConfigData data = service.get();
    assertEquals(AiProviderConfigData.Source.ENV, data.getSource());
    assertEquals(List.of("groq/llama-env"), data.getEnvChain());
  }

  // ---- save, encryption, never returning the key ----

  @Test
  void saveStoresOnlyCiphertextAndReturnsNoKey() throws Exception {
    AiProviderConfigData data =
        service.save(request("anthropic", "", "claude-x", CLAUDE_KEY), ADMIN);

    AiProviderConfigEntity stored = row.get();
    assertFalse(stored.getApiKeyCiphertext().contains(CLAUDE_KEY));
    assertEquals(
        CLAUDE_KEY,
        cipher.decrypt(stored.getApiKeyCiphertext(), AiProviderConfigEntity.KEY_CIPHER_CONTEXT));
    assertEquals("anthropic", stored.getProvider());
    assertEquals(anthropic.defaultEndpoint(), stored.getEndpoint(), "blank endpoint = default");
    assertEquals(ADMIN, stored.getUpdatedBy());
    assertTrue(stored.getUpdatedAt() != null);

    assertTrue(data.isHasKey());
    assertEquals("AAAA", data.getKeyLast4());
    assertKeyAbsent(json.writeValueAsString(data), stored);
    assertKeyAbsent(json.writeValueAsString(service.get()), stored);
  }

  @Test
  void blankKeyOnUpdateKeepsTheSavedKey() {
    service.save(request("anthropic", "", "claude-x", CLAUDE_KEY), ADMIN);
    String ciphertextBefore = row.get().getApiKeyCiphertext();

    service.save(
        request("anthropic", "https://anthropic.example.com/v2/messages", "claude-y", "  "), ADMIN);

    assertEquals(ciphertextBefore, row.get().getApiKeyCiphertext());
    assertEquals("claude-y", row.get().getModel());
    assertEquals("https://anthropic.example.com/v2/messages", row.get().getEndpoint());
    assertFalse(audits.get(1).isKeyChanged());
  }

  @Test
  void savedKeyIsNeverSentToADifferentServer() {
    service.save(request("anthropic", "", "claude-x", CLAUDE_KEY), ADMIN);
    String endpointBefore = row.get().getEndpoint();

    IllegalArgumentException onSave =
        assertThrows(
            IllegalArgumentException.class,
            () ->
                service.save(
                    request("anthropic", "https://evil.example.com/v1/messages", "claude-x", ""),
                    ADMIN));
    assertTrue(onSave.getMessage().contains("Enter the API key again"), onSave.getMessage());
    assertEquals(endpointBefore, row.get().getEndpoint(), "refused save changes nothing");

    calls.clear();
    assertThrows(
        IllegalArgumentException.class,
        () -> service.test(request("anthropic", "https://evil.example.com/v1/messages", "", "")));
    assertTrue(calls.isEmpty(), "no call is made with the saved key to the new host");

    // Same host on another port is a different server too.
    assertThrows(
        IllegalArgumentException.class,
        () ->
            service.test(
                request("anthropic", "https://anthropic.example.com:8443/default", "", "")));
  }

  @Test
  void newHostWorksWhenTheKeyIsEnteredAgain() {
    service.save(request("anthropic", "", "claude-x", CLAUDE_KEY), ADMIN);

    service.save(
        request(
            "anthropic",
            "https://proxy.example.com/v1/messages",
            "claude-x",
            "sk-ant-proxy-key-DDDD"),
        ADMIN);

    assertEquals("https://proxy.example.com/v1/messages", row.get().getEndpoint());
    assertEquals("DDDD", row.get().getApiKeyLast4());
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
  void newKeyReplacesTheSavedKey() {
    service.save(request("anthropic", "", "claude-x", CLAUDE_KEY), ADMIN);
    String ciphertextBefore = row.get().getApiKeyCiphertext();

    service.save(request("anthropic", "", "claude-x", "sk-ant-rotated-key-CCCC"), ADMIN);

    assertNotEquals(ciphertextBefore, row.get().getApiKeyCiphertext());
    assertEquals("CCCC", row.get().getApiKeyLast4());
  }

  @Test
  void switchingProviderRequiresANewKey() {
    service.save(request("anthropic", "", "claude-x", CLAUDE_KEY), ADMIN);

    assertThrows(
        IllegalArgumentException.class,
        () -> service.save(request("openai", "", "gpt-x", ""), ADMIN));
    assertEquals("anthropic", row.get().getProvider(), "a refused save changes nothing");
  }

  @Test
  void firstSaveRequiresAKey() {
    assertThrows(
        IllegalArgumentException.class,
        () -> service.save(request("anthropic", "", "claude-x", null), ADMIN));
    assertNull(row.get());
  }

  @Test
  void rejectsUnknownProviderBadEndpointAndBadModel() {
    assertThrows(
        IllegalArgumentException.class,
        () -> service.save(request("mistral", "", "m", CLAUDE_KEY), ADMIN));
    assertThrows(
        IllegalArgumentException.class,
        () -> service.save(request("anthropic", "http://api.example.com", "m", CLAUDE_KEY), ADMIN));
    assertThrows(
        IllegalArgumentException.class,
        () -> service.save(request("anthropic", "", "  ", CLAUDE_KEY), ADMIN));
    assertThrows(
        IllegalArgumentException.class,
        () -> service.save(request("anthropic", "", "bad model", CLAUDE_KEY), ADMIN));
    assertNull(row.get());
    verify(repository, never()).save(any());
  }

  @Test
  void openAiCompatibleNeedsAnEndpointAndKeepsTheOneGiven() {
    IllegalArgumentException e =
        assertThrows(
            IllegalArgumentException.class,
            () ->
                service.save(request("openai-compatible", " ", "deepseek/r1", OPENAI_KEY), ADMIN));
    assertTrue(e.getMessage().contains("Endpoint is required"), e.getMessage());
    assertNull(row.get());

    service.save(
        request(
            "openai-compatible",
            "https://openrouter.example.com/api/v1",
            "deepseek/r1",
            OPENAI_KEY),
        ADMIN);
    assertEquals("https://openrouter.example.com/api/v1", row.get().getEndpoint());
    assertTrue(
        service.get().getAvailableProviders().stream()
            .anyMatch(
                p -> p.getName().equals("openai-compatible") && p.getDefaultEndpoint() == null));
  }

  @Test
  void refusesToStoreAKeyWithoutAnEncryptionKey() {
    service =
        new AiProviderConfigService(
            repository,
            auditRepository,
            mock(UserRepository.class),
            new SecretCipher(""),
            new EndpointValidator(permissive()),
            chain,
            resolver);

    DownstreamServiceException e =
        assertThrows(
            DownstreamServiceException.class,
            () -> service.save(request("anthropic", "", "claude-x", CLAUDE_KEY), ADMIN));
    assertEquals("ENCRYPTION_NOT_CONFIGURED", e.getType());
    assertNull(row.get());
  }

  @Test
  void auditRecordsWhoAndWhatButNoKey() throws Exception {
    service.save(request("anthropic", "", "claude-x", CLAUDE_KEY), ADMIN);
    service.reset(ADMIN);

    assertEquals(2, audits.size());
    AiProviderConfigAuditEntity saved = audits.get(0);
    assertEquals(AiProviderConfigAuditEntity.Action.SAVED, saved.getAction());
    assertEquals(ADMIN, saved.getChangedBy());
    assertTrue(saved.isKeyChanged());
    assertEquals(AiProviderConfigAuditEntity.Action.RESET, audits.get(1).getAction());
    String auditJson = json.writeValueAsString(audits);
    assertFalse(auditJson.contains(CLAUDE_KEY));
    assertFalse(auditJson.contains("AAAA"));
  }

  // ---- runtime switching, no restart ----

  @Test
  void savedConfigTakesEffectOnTheNextQuizWithoutARestart() {
    assertEquals("groq/llama-env", generateQuiz());

    service.save(request("anthropic", "", "claude-x", CLAUDE_KEY), ADMIN);
    calls.clear();
    assertEquals("anthropic/claude-x", generateQuiz());
    assertTrue(
        calls.stream().allMatch(c -> c.endsWith(":claude-x:" + CLAUDE_KEY)), calls.toString());

    service.save(
        request("openai", "https://openai.example.com/v1/chat", "gpt-x", OPENAI_KEY), ADMIN);
    calls.clear();
    assertEquals("openai/gpt-x", generateQuiz());
    assertTrue(
        calls.stream()
            .allMatch(
                c -> c.equals("openai:https://openai.example.com/v1/chat:gpt-x:" + OPENAI_KEY)),
        calls.toString());

    service.reset(ADMIN);
    calls.clear();
    assertEquals("groq/llama-env", generateQuiz());
  }

  @Test
  void failingSavedConfigIsAClearErrorNotASilentFallback() {
    service.save(request("anthropic", "", "claude-x", CLAUDE_KEY), ADMIN);
    anthropic.next =
        AiResult.keyFailure(
            AiFailure.AUTH_FAILED,
            "401 invalid x-api-key " + CLAUDE_KEY + " sk-ant-echoed-1234567");

    AiProviderException e = assertThrows(AiProviderException.class, this::generateQuiz);

    assertTrue(e.getMessage().contains("rejected the API key"), e.getMessage());
    assertFalse(e.getMessage().contains(CLAUDE_KEY));
    assertFalse(e.getMessage().contains("sk-ant-echoed"));
    assertTrue(calls.stream().noneMatch(c -> c.startsWith("groq:")), "no fallback to .env");
    assertEquals("anthropic", row.get().getProvider(), "a failing call never touches the config");
  }

  @Test
  void undecryptableSavedKeyIsAClearError() {
    service.save(request("anthropic", "", "claude-x", CLAUDE_KEY), ADMIN);
    // Same row, read by a server with a different master key.
    AiConfigResolver otherServer =
        new AiConfigResolver(repository, new SecretCipher(randomKey()), new AiConfigProperties());

    assertThrows(AiProviderException.class, otherServer::active);
  }

  // ---- test connection ----

  @Test
  void testConnectionUsesSubmittedValuesAndSavesNothing() {
    AiConnectionTestData result =
        service.test(request("openai", "https://openai.example.com/v1/chat", "gpt-x", OPENAI_KEY));

    assertTrue(result.isSuccess());
    assertEquals(List.of("openai:https://openai.example.com/v1/chat:gpt-x:" + OPENAI_KEY), calls);
    assertNull(row.get());
  }

  @Test
  void testConnectionFallsBackToTheSavedKey() {
    service.save(request("anthropic", "", "claude-x", CLAUDE_KEY), ADMIN);

    assertTrue(service.test(request("", "", "", "")).isSuccess());
    assertEquals(
        List.of("anthropic:" + anthropic.defaultEndpoint() + ":claude-x:" + CLAUDE_KEY), calls);
  }

  @Test
  void testConnectionReturnsASanitizedError() {
    openai.next = AiResult.keyFailure(AiFailure.AUTH_FAILED, "401 Incorrect API key " + OPENAI_KEY);

    AiConnectionTestData result = service.test(request("openai", "", "gpt-x", OPENAI_KEY));

    assertFalse(result.isSuccess());
    assertFalse(result.getMessage().contains(OPENAI_KEY));
    assertTrue(result.getMessage().contains("rejected the API key"));
  }

  // ---- helpers ----

  /** Runs real quiz generation and returns which provider/model wrote the quiz. */
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
      String provider, String endpoint, String model, String apiKey) {
    AiProviderConfigRequest r = new AiProviderConfigRequest();
    r.setProvider(provider);
    r.setEndpoint(endpoint);
    r.setModel(model);
    r.setApiKey(apiKey);
    return r;
  }

  private static AiProviderConfigEntity copy(AiProviderConfigEntity e) {
    return AiProviderConfigEntity.builder()
        .id(e.getId())
        .provider(e.getProvider())
        .endpoint(e.getEndpoint())
        .model(e.getModel())
        .apiKeyCiphertext(e.getApiKeyCiphertext())
        .apiKeyLast4(e.getApiKeyLast4())
        .updatedBy(e.getUpdatedBy())
        .updatedAt(e.getUpdatedAt())
        .version(e.getVersion())
        .build();
  }

  private static String randomKey() {
    byte[] key = new byte[32];
    new SecureRandom().nextBytes(key);
    return Base64.getEncoder().encodeToString(key);
  }

  /** Answers quiz and evaluation prompts with valid JSON, or {@link #next} once if set. */
  private class FakeProvider implements AiProvider {
    private final String name;
    AiResult next;

    FakeProvider(String name) {
      this.name = name;
    }

    @Override
    public String name() {
      return name;
    }

    @Override
    public String defaultEndpoint() {
      return "https://" + name + ".example.com/default";
    }

    @Override
    public AiResult call(
        String endpoint, String model, String apiKey, String prompt, int maxTokens) {
      calls.add(name + ":" + endpoint + ":" + model + ":" + apiKey);
      if (next != null) {
        return next;
      }
      if (prompt.contains("quiz evaluator")) {
        return AiResult.success("{\"is_evaluation_passed\": true, \"rejection_reason\": null}");
      }
      return AiResult.success(
          "{\"question\": \"Which road is famous here?\", \"options\": [\"100 Feet Road\","
              + " \"MG Road\", \"Brigade Road\", \"Church Street\"], \"answer_index\": 1,"
              + " \"explanation\": \"It is lined with cafes.\"}");
    }
  }
}
