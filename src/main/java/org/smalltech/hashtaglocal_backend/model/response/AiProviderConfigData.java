package org.smalltech.hashtaglocal_backend.model.response;

import java.time.LocalDateTime;
import java.util.List;
import lombok.Builder;
import lombok.Value;

/**
 * The AI connection as the Ops Dashboard sees it. Never carries the key or its ciphertext: only
 * {@code hasKey} and the last four characters.
 */
@Value
@Builder
public class AiProviderConfigData {

  public enum Source {
    /** Saved from the Ops Dashboard. */
    DATABASE,
    /** Nothing saved: quiz generation uses the ai.quiz.chain from .env. */
    ENV
  }

  Source source;

  /** The admin's own name for the connection. */
  String name;

  /** OpenAI-compatible endpoint. */
  String endpoint;

  String model;
  boolean hasKey;
  String keyLast4;
  LocalDateTime updatedAt;
  Long updatedBy;
  String updatedByName;

  /** False when AI_CONFIG_ENCRYPTION_KEY is missing, so a key cannot be saved. */
  boolean encryptionConfigured;

  /** The .env chain's provider/model pairs, used when {@code source} is ENV. */
  List<String> envChain;
}
