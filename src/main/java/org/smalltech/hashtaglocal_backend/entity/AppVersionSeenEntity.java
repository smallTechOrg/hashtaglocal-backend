package org.smalltech.hashtaglocal_backend.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.smalltech.hashtaglocal_backend.model.Platform;

/**
 * The app version a login session reported during one ISO week: one row per session per week,
 * holding the last version seen that week. {@link UserAuthSessionEntity#getAppVersion()} only keeps
 * the current version, so this is what the weekly version metrics are computed from.
 *
 * <p>Plain ids, not foreign keys: sessions are deleted by cleanup jobs, and the history must stay.
 */
@Entity
@Table(
    name = "app_version_seen",
    uniqueConstraints =
        @UniqueConstraint(
            name = "uk_app_version_seen_session_week",
            columnNames = {"session_id", "week_year", "week_number"}),
    indexes = @Index(name = "idx_app_version_seen_week", columnList = "week_year, week_number"))
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AppVersionSeenEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(nullable = false)
  private Long sessionId;

  @Column(nullable = false)
  private Long userId;

  @Enumerated(EnumType.STRING)
  @Column(length = 15)
  private Platform platform;

  @Column(nullable = false, length = 50)
  private String appVersion;

  @Column(nullable = false)
  private Integer weekYear;

  @Column(nullable = false)
  private Integer weekNumber;

  @Column(nullable = false)
  private LocalDateTime lastSeenAt;
}
