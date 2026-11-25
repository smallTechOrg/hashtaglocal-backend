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
 * endpoint, a model and a key.
 *
 * <p>Every Save adds a new row with {@code isActive = true} and sets the previous one inactive, so
 * the table keeps every version. "Remove key" sets the active row inactive. At most one row is
 * active; when none is, quiz generation uses the {@code ai.quiz.chain} from {@code .env}.
 *
 * <p>The API key is stored only as AES-GCM ciphertext (see {@code SecretCipher}), never sent back
 * by any endpoint, and kept only on the active row: it is cleared when a row is set inactive, so
 * the history keeps who changed what and when, but no old keys. {@code apiKeyLast4} stays on every
 * row so the history still shows which key was in use.
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

  /** The admin's own name for the connection, e.g. "OpenRouter DeepSeek". */
  @Column(nullable = false, length = 100)
  private String name;

  /** OpenAI-compatible base URL (…/v1) or full …/chat/completions URL. */
  @Column(nullable = false, length = 500)
  private String endpoint;

  @Column(nullable = false, length = 200)
  private String model;

  /** Encrypted API key; only the active row has one. */
  @ToString.Exclude
  @Column(name = "api_key_ciphertext", columnDefinition = "text")
  private String apiKeyCiphertext;

  @Column(name = "api_key_last4", length = 4)
  private String apiKeyLast4;

  /** True for the connection in effect. At most one row is active. */
  @Builder.Default
  @Column(name = "is_active", nullable = false)
  private Boolean isActive = true;

  /** User id of the admin who created this row (and entered or kept its key). */
  @Column(nullable = false, updatable = false)
  private Long createdBy;

  /** User id of the admin who last changed this row: its creator, or whoever set it inactive. */
  @Column(nullable = false)
  private Long updatedBy;

  /** When this row was added. Set by Hibernate on insert. */
  @CreationTimestamp
  @Column(nullable = false, updatable = false)
  private LocalDateTime createdAt;

  /** When this row was last changed, e.g. set inactive. Set automatically. */
  @UpdateTimestamp
  @Column(nullable = false)
  private LocalDateTime updatedAt;
}
