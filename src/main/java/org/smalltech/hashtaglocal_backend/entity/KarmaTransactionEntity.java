package org.smalltech.hashtaglocal_backend.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.smalltech.hashtaglocal_backend.model.KarmaTransactionStatus;
import org.smalltech.hashtaglocal_backend.model.KarmaTransactionType;

@Entity
@Table(
    name = "karma_transactions",
    indexes = {
      @Index(name = "idx_karma_user_status", columnList = "user_id, status"),
      @Index(name = "idx_karma_reference_action", columnList = "reference_action_id")
    })
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class KarmaTransactionEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "user_id", nullable = false)
  private UserEntity userEntity;

  @Column(nullable = false)
  private int points;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 40)
  private KarmaTransactionType type;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 20)
  private KarmaTransactionStatus status;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "reference_action_id")
  private IssueActionEntity referenceAction;

  @CreationTimestamp
  @Column(nullable = false, updatable = false)
  private LocalDateTime createdAt;
}
