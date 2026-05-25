package org.smalltech.hashtaglocal_backend.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import lombok.*;
import org.smalltech.hashtaglocal_backend.model.EventApprovalStatus;

/**
 * JPA entity for the {@code event_approvals} table.
 *
 * <p>Tracks whether a scraped event has been approved or rejected for display on the public
 * frontend. The {@code event_id} is both the primary key and a foreign key to {@code events.id}.
 *
 * <p>All newly imported events get a row with {@link EventApprovalStatus#PENDING}. An admin
 * approves or rejects them via the ops portal ({@code /admin/event/*} endpoints).
 *
 * <p>The optional {@code display_name} lets admins override the scraped event name with a cleaner
 * title for the website — without touching the original data in the {@code events} table.
 */
@Entity
@Table(name = "event_approvals")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class EventApprovalEntity {

  /** Same value as {@code events.id} — acts as both PK and implicit FK. */
  @Id
  @Column(name = "event_id")
  private Long eventId;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 20)
  @Builder.Default
  private EventApprovalStatus status = EventApprovalStatus.PENDING;

  /** Admin-provided display name override. Null means use the original scraped name. */
  @Column(name = "display_name", length = 500)
  private String displayName;

  /** Timestamp when an admin approved or rejected this event. Null while still pending. */
  @Column(name = "reviewed_at")
  private LocalDateTime reviewedAt;

  @Column(name = "created_at", nullable = false, updatable = false)
  private LocalDateTime createdAt;

  @PrePersist
  protected void onCreate() {
    createdAt = LocalDateTime.now();
  }
}
