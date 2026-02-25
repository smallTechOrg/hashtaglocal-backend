package org.smalltech.hashtaglocal_backend.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

@Entity
@Table(
    name = "user_auth_providers",
    uniqueConstraints = {
      @UniqueConstraint(
          name = "uk_provider_id_type",
          columnNames = {"provider_type", "provider_user_id"}),
      @UniqueConstraint(
          name = "uk_user_provider_type",
          columnNames = {"provider_type", "user_id"})
    })
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserAuthProviderEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "user_id", nullable = false)
  private UserEntity user;

  @Column(nullable = false, length = 50)
  private String providerType; // e.g. Google, Apple

  @Column(nullable = false, length = 200)
  private String providerUserId;

  @Column(length = 200)
  private String email;

  @CreationTimestamp
  @Column(nullable = false, updatable = false)
  private LocalDateTime createdAt;

  @UpdateTimestamp
  @Column(nullable = false)
  private LocalDateTime updatedAt;
}
