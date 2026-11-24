package org.smalltech.hashtaglocal_backend.service.ai;

import java.util.regex.Pattern;

/**
 * Masks API keys in text that may reach a log or an API response (provider error messages,
 * exception messages). Masks the exact key when it is known, plus anything shaped like a provider
 * key or a long opaque token.
 */
public final class SecretRedactor {

  static final String MASK = "[REDACTED]";

  // sk-..., sk-ant-..., sk-proj-... (OpenAI/Anthropic, possibly partly starred), gsk_... (Groq),
  // AIza... (Google), "Bearer <token>", and any other 32+ char opaque token.
  private static final Pattern KEY_SHAPED =
      Pattern.compile(
          "(?i)(sk-[A-Za-z0-9_\\-*.]{6,}|gsk_[A-Za-z0-9_\\-*]{6,}|AIza[0-9A-Za-z_\\-*]{10,}"
              + "|bearer\\s+[A-Za-z0-9._\\-~+/=*]{6,}|[A-Za-z0-9_\\-]{32,})");

  private SecretRedactor() {}

  /** {@code text} with key-shaped substrings masked; null stays null. */
  public static String redact(String text) {
    return text == null ? null : KEY_SHAPED.matcher(text).replaceAll(MASK);
  }

  /** As {@link #redact(String)}, and also masks every occurrence of {@code knownKey}. */
  public static String redact(String text, String knownKey) {
    if (text == null) {
      return null;
    }
    String result = text;
    if (knownKey != null && knownKey.length() >= 4) {
      result = result.replace(knownKey, MASK);
    }
    return redact(result);
  }
}
