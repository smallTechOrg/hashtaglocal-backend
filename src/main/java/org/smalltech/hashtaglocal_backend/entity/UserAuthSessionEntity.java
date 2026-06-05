package org.smalltech.hashtaglocal_backend.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import org.smalltech.hashtaglocal_backend.model.Platform;

@Entity
@Table(name = "user_auth_sessions")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserAuthSessionEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "user_auth_provider_id", nullable = false)
  private UserAuthProviderEntity userAuthProvider;

  @Column(length = 2000)
  private String deviceId;

  @Column(columnDefinition = "text")
  private String notificationToken;

  @Enumerated(EnumType.STRING)
  @Column(length = 15)
  private Platform platform;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "user_id", nullable = false)
  private UserEntity user;

  @Column(columnDefinition = "text")
  private String accessToken;

  private Long accessTokenExpiryTs;

  @Column(columnDefinition = "text")
  private String refreshToken;

  private Long refreshTokenExpiryTs;

  @CreationTimestamp
  @Column(nullable = false, updatable = false)
  private LocalDateTime createdAt;

  @UpdateTimestamp
  @Column(nullable = false)
  private LocalDateTime updatedAt;

  @Builder.Default
  @Column(nullable = false)
  private Boolean isActive = true;
}
