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
import org.smalltech.hashtaglocal_backend.model.AccountDeletionRequestStatus;

/**
 * Created to persist user-initiated account deletion requests in the
 * account_deletion_requests table. Requests start as PENDING and are resolved manually
 * by an admin within 24 hours of creation.
 */
@Entity
@Table(name = "account_deletion_requests")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AccountDeletionRequestEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "user_id", nullable = false)
  private UserEntity user;

  /** PENDING requests are the source of truth for the 24-hour manual deletion queue. */
  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 20)
  private AccountDeletionRequestStatus status;

  @Column(nullable = false)
  private LocalDateTime requestedAt;

  @Column(nullable = false)
  private LocalDateTime scheduledDeletionAt;

  @CreationTimestamp
  @Column(nullable = false, updatable = false)
  private LocalDateTime createdAt;

  @UpdateTimestamp
  @Column(nullable = false)
  private LocalDateTime updatedAt;
}
