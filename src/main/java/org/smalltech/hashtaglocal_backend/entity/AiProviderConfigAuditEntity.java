package org.smalltech.hashtaglocal_backend.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * One change to {@link AiProviderConfigEntity}: who, when, and the settings after the change. Holds
 * no part of the API key, only whether it was replaced.
 */
@Entity
@Table(name = "ai_provider_config_audit")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AiProviderConfigAuditEntity {

  public enum Action {
    /** Config created or updated. */
    SAVED,
    /** Config deleted; quiz generation went back to the .env chain. */
    RESET
  }

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 20)
  private Action action;

  /** Settings after the change; null for RESET. */
  @Column(length = 50)
  private String provider;

  @Column(length = 500)
  private String endpoint;

  @Column(length = 200)
  private String model;

  @Column(nullable = false)
  private boolean keyChanged;

  @Column(nullable = false)
  private Long changedBy;

  @Column(nullable = false)
  private LocalDateTime changedAt;
}
