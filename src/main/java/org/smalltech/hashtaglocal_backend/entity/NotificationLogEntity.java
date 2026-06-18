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
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import org.smalltech.hashtaglocal_backend.model.NotificationSource;
import org.smalltech.hashtaglocal_backend.model.NotificationType;

@Entity
@Table(name = "notification_log")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class NotificationLogEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Enumerated(EnumType.STRING)
  @Column(length = 10, nullable = false)
  private NotificationSource source;

  // Polymorphic pointer to the triggering record — no FK by design (references different tables)
  @Column(length = 50)
  private String sourceRefType;

  private Long sourceRefId;

  @Enumerated(EnumType.STRING)
  @Column(length = 20, nullable = false)
  private NotificationType notificationType;

  @Column(length = 500, nullable = false)
  private String title;

  @Column(columnDefinition = "text", nullable = false)
  private String body;

  // FCM data map serialised as JSON (issueId, status, etc.)
  @Column(columnDefinition = "jsonb", nullable = false)
  private String payload;

  // Null until all FCM calls finish, then updated to total tokens attempted
  private Integer recipientCount;

  @CreationTimestamp
  @Column(nullable = false, updatable = false)
  private LocalDateTime createdAt;

  @UpdateTimestamp
  @Column(nullable = false)
  private LocalDateTime updatedAt;
}
