package org.smalltech.hashtaglocal_backend.model.response;

import lombok.Builder;
import lombok.Data;

/** Read-only view of a Groq prompt template, exposed to the ops portal. */
@Data
@Builder
public class AiPromptData {

  /** Stable identifier — matches the constant name in {@code GroqClient}. */
  private String key;

  /** Human-readable label shown in the ops UI. */
  private String description;

  /** Prompt text with {@code {{placeholder}}} tokens. */
  private String template;

  /** Comma-separated list of valid placeholders for the ops UI hint. */
  private String variables;
}
