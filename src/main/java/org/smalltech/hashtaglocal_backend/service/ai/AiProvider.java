package org.smalltech.hashtaglocal_backend.service.ai;

/** One AI API (Anthropic, Groq, Gemini). Never throws: every failure comes back as an AiResult. */
public interface AiProvider {

  /** Name used in config ({@code ai.quiz.chain[].provider}) and logs, e.g. "groq". */
  String name();

  /** Sends one user prompt to {@code model} using {@code apiKey}. */
  AiResult call(String model, String apiKey, String prompt, int maxTokens);
}
