package org.smalltech.hashtaglocal_backend.service.ai;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.io.ClassPathResource;

/** The real application.yaml binds env vars into the chain as expected. */
class QuizAiPropertiesBindingTest {

  @Test
  void commaSeparatedEnvVarsBecomeOrderedLists() throws Exception {
    QuizAiProperties props =
        bind(
            Map.of(
                "QUIZ_ANTHROPIC_KEYS", "c1,c2,c3",
                "QUIZ_GROQ_KEYS", "g1,g2",
                "QUIZ_GROQ_MODELS", "model-a,model-b",
                "QUIZ_GEMINI_KEYS", "m1",
                "QUIZ_GEMINI_MODELS", "flash"));

    List<QuizAiProperties.Level> chain = props.getChain();
    assertEquals(
        List.of("anthropic", "groq", "gemini"), chain.stream().map(l -> l.getProvider()).toList());
    assertEquals(List.of("claude-sonnet-5-5", "claude-haiku-5-5"), chain.get(0).getModels());
    assertEquals(List.of("c1", "c2", "c3"), chain.get(0).getKeys());
    assertEquals(List.of("model-a", "model-b"), chain.get(1).getModels());
    assertEquals(List.of("g1", "g2"), chain.get(1).getKeys());
    assertEquals(List.of("m1"), chain.get(2).getKeys());
  }

  @Test
  void existingSingleAnthropicKeyStillWorks() throws Exception {
    QuizAiProperties props = bind(Map.of("ANTHROPIC_API_KEY", "old-key"));

    assertEquals(List.of("old-key"), props.getChain().get(0).getKeys());
    assertEquals(List.of(), props.getChain().get(1).getKeys());
  }

  private static QuizAiProperties bind(Map<String, Object> env) throws Exception {
    StandardEnvironment environment = new StandardEnvironment();
    // Replace real OS env vars so the test does not depend on the machine.
    environment
        .getPropertySources()
        .remove(StandardEnvironment.SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME);
    environment.getPropertySources().addFirst(new MapPropertySource("env", env));
    new YamlPropertySourceLoader()
        .load("application", new ClassPathResource("application.yaml"))
        .forEach(source -> environment.getPropertySources().addLast(source));
    return Binder.get(environment).bind("ai.quiz", QuizAiProperties.class).get();
  }
}
