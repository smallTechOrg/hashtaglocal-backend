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
import org.smalltech.hashtaglocal_backend.model.IssueActionApprovalStatus;
import org.smalltech.hashtaglocal_backend.model.IssueActionModel;

@Entity
@Table(name = "issue_actions")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class IssueActionEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "issue_id")
  private IssueEntity issueEntity;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "user_id")
  private UserEntity userEntity;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 100)
  private IssueActionModel action;

  /**
   * Whether this action requires, is awaiting, or has received admin approval.
   *
   * <p>REPORT / VERIFY / RESOLVE actions are created with {@code PENDING}; REJECT and UPDATE use
   * {@code NOT_REQUIRED}.
   */
  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 20)
  private IssueActionApprovalStatus approvalStatus;

  /** The admin who approved or rejected this action. {@code null} when not yet reviewed. */
  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "approved_by_user_id")
  private UserEntity approvedByUser;

  /** Timestamp of when the admin reviewed this action. */
  @Column private LocalDateTime approvedAt;

  /**
   * The single media item attached to this action. Populated for VERIFY and RESOLVE actions that
   * include media; {@code null} for REPORT, REJECT, and UPDATE actions.
   */
  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "media_id")
  private MediaEntity media;

  @Column(nullable = false, updatable = false)
  private LocalDateTime createdAt;
}
