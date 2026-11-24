package org.smalltech.hashtaglocal_backend.service.ai;

/**
 * One AI API (Anthropic, OpenAI, Groq, Gemini). Never throws: every failure comes back as an
 * AiResult.
 *
 * <p>To add a provider, add a {@code @Component} implementing this (usually by extending {@link
 * RestAiProvider}). Its {@link #name()} is then accepted in the {@code .env} chain and in the Ops
 * Dashboard's AI Provider settings; nothing else needs registering.
 */
public interface AiProvider {

  /** Name used in config ({@code ai.quiz.chain[].provider}, the DB config) and logs. */
  String name();

  /** Human-readable name for the Ops Dashboard, e.g. "Claude (Anthropic)". */
  default String displayName() {
    return name();
  }

  /** The provider's public API URL, used when no endpoint is configured. */
  String defaultEndpoint();

  /** Sends one user prompt to {@code model} at {@code endpoint} using {@code apiKey}. */
  AiResult call(String endpoint, String model, String apiKey, String prompt, int maxTokens);
}
