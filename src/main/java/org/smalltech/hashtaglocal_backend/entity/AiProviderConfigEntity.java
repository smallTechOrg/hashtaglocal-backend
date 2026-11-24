package org.smalltech.hashtaglocal_backend.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.ToString;

/**
 * The AI provider that quiz generation uses, as set by an admin in the Ops Dashboard. A single row
 * ({@link #SINGLETON_ID}); when it is absent, quiz generation uses the {@code ai.quiz.chain} from
 * {@code .env}.
 *
 * <p>The API key is stored only as AES-GCM ciphertext (see {@code SecretCipher}) and is never sent
 * back by any endpoint; {@code apiKeyLast4} is what the dashboard shows instead.
 */
@Entity
@Table(name = "ai_provider_config")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AiProviderConfigEntity {

  public static final long SINGLETON_ID = 1L;

  /** Associated data bound into the ciphertext, so it only decrypts as this column. */
  public static final String KEY_CIPHER_CONTEXT = "ai_provider_config.api_key";

  @Id private Long id;

  /** An {@code AiProvider} name, e.g. anthropic or openai. */
  @Column(nullable = false, length = 50)
  private String provider;

  @Column(nullable = false, length = 500)
  private String endpoint;

  @Column(nullable = false, length = 200)
  private String model;

  @ToString.Exclude
  @Column(name = "api_key_ciphertext", nullable = false, columnDefinition = "text")
  private String apiKeyCiphertext;

  @Column(name = "api_key_last4", length = 4)
  private String apiKeyLast4;

  /** User id of the admin who saved this. */
  @Column(nullable = false)
  private Long updatedBy;

  @Column(nullable = false)
  private LocalDateTime updatedAt;

  /** Optimistic lock, so two admins saving at once cannot interleave half-updates. */
  @Version private Long version;
}
