package org.smalltech.hashtaglocal_backend.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import lombok.*;
import org.smalltech.hashtaglocal_backend.model.FeedPostKind;
import org.smalltech.hashtaglocal_backend.model.FeedPostStatus;

/**
 * The spine of the per-hashtag feed timeline. One row per post; the actual content lives in the 1:1
 * {@link FeedPostContentEntity} (shared primary key). See FEED_DESIGN.md §2 / §4.1.
 *
 * <p>The public timeline pages over this table filtered by {@code locality + status=PUBLISHED},
 * ordered by {@code (createdAt, id)} DESC (keyset pagination).
 */
@Entity
@Table(
    name = "feed_posts",
    indexes = {
      @Index(name = "idx_feed_posts_timeline", columnList = "locality_id, status, created_at, id"),
      @Index(name = "idx_feed_posts_pinned", columnList = "locality_id, pinned"),
      @Index(name = "idx_feed_posts_moderation", columnList = "status, created_at")
    })
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class FeedPostEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  /** The #hashtag channel this post belongs to. */
  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "locality_id", nullable = false)
  private Locality locality;

  /** The author. {@code null} for system-generated posts (auto issue-refs, bulletins). */
  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "author_user_id")
  private UserEntity author;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 30)
  private FeedPostKind kind;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 20)
  private FeedPostStatus status;

  @Column(nullable = false)
  @Builder.Default
  private boolean pinned = false;

  /** Optional geo-tag for the post itself (distinct from the locality channel). */
  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "location_id")
  private Location location;

  /** The universal content row (FKs + generic columns + JSONB tail). */
  @OneToOne(
      mappedBy = "post",
      cascade = CascadeType.ALL,
      fetch = FetchType.LAZY,
      orphanRemoval = true)
  private FeedPostContentEntity content;

  /** AI verdict + admin override audit. Populated once moderation runs. */
  @OneToOne(
      mappedBy = "post",
      cascade = CascadeType.ALL,
      fetch = FetchType.LAZY,
      orphanRemoval = true)
  private FeedModerationEntity moderation;

  /**
   * When the post became (or is scheduled to become) public. A future value supports scheduled
   * bulletins (Phase 2); the read query filters {@code published_at <= now}.
   */
  @Column private LocalDateTime publishedAt;

  @Column(nullable = false, updatable = false)
  private LocalDateTime createdAt;

  @Column(nullable = false)
  private LocalDateTime updatedAt;

  @PrePersist
  protected void onCreate() {
    LocalDateTime now = LocalDateTime.now();
    createdAt = now;
    updatedAt = now;
  }

  @PreUpdate
  protected void onUpdate() {
    updatedAt = LocalDateTime.now();
  }
}
