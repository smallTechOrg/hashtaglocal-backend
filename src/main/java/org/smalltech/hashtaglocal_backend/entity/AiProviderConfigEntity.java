package org.smalltech.hashtaglocal_backend.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.ToString;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

/**
 * The AI connection that quiz generation uses, as set by an admin in the Ops Dashboard, and the log
 * of every change to it. A connection is a name the admin chooses plus an OpenAI-compatible
 * endpoint, a model and a key. Rows are only ever added: each Save adds a row with the new
 * settings, and "Remove key" adds a row with no connection. The newest row is the config in effect;
 * when it has no connection (or there are no rows), quiz generation uses the {@code ai.quiz.chain}
 * from {@code .env}.
 *
 * <p>The API key is stored only as AES-GCM ciphertext (see {@code SecretCipher}), never sent back
 * by any endpoint, and kept only on the newest row: older rows have it cleared when a newer row is
 * added, so the history keeps who changed what and when, but no old keys. {@code apiKeyLast4} stays
 * on every row so the history still shows which key was in use.
 */
@Entity
@Table(name = "ai_configs")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AiProviderConfigEntity {

  /** Associated data bound into the ciphertext, so it only decrypts as this column. */
  public static final String KEY_CIPHER_CONTEXT = "ai_configs.api_key";

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  /**
   * The admin's own name for the connection, e.g. "OpenRouter DeepSeek". Null on a "removed" row.
   */
  @Column(length = 100)
  private String name;

  /** OpenAI-compatible base URL (…/v1) or full …/chat/completions URL. */
  @Column(length = 500)
  private String endpoint;

  @Column(length = 200)
  private String model;

  /** Encrypted API key; only the newest row has one. */
  @ToString.Exclude
  @Column(name = "api_key_ciphertext", columnDefinition = "text")
  private String apiKeyCiphertext;

  @Column(name = "api_key_last4", length = 4)
  private String apiKeyLast4;

  /** True when this change entered a new key (or removed it), false when it kept the old one. */
  @Column(nullable = false)
  private boolean keyChanged;

  /** User id of the admin who made this change. */
  @Column(nullable = false)
  private Long updatedBy;

  /** When this row (this change) was added. Set by Hibernate on insert. */
  @CreationTimestamp
  @Column(nullable = false, updatable = false)
  private LocalDateTime createdAt;

  /**
   * When this row was last written. Set by Hibernate on insert and update; also moves when the key
   * is cleared from an older row.
   */
  @UpdateTimestamp
  @Column(nullable = false)
  private LocalDateTime updatedAt;

  /** True for a row that sets a connection; false for a "removed" row (back to .env). */
  public boolean isActive() {
    return endpoint != null;
  }
}
