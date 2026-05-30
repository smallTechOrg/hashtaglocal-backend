package org.smalltech.hashtaglocal_backend.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import lombok.*;
import org.smalltech.hashtaglocal_backend.model.AdminModerationAction;
import org.smalltech.hashtaglocal_backend.model.AiCategory;
import org.smalltech.hashtaglocal_backend.model.AiVerdict;

/**
 * AI moderation verdict plus any human override for a feed post — 1:1 with {@link FeedPostEntity}
 * (shared PK). This is the read model behind the admin moderation page and the full audit trail.
 * See FEED_DESIGN.md §8.
 */
@Entity
@Table(name = "feed_moderation")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class FeedModerationEntity {

  @Id private Long feedPostId;

  @OneToOne(fetch = FetchType.LAZY)
  @MapsId
  @JoinColumn(name = "feed_post_id")
  private FeedPostEntity post;

  // --- AI verdict ---

  @Enumerated(EnumType.STRING)
  @Column(length = 20)
  private AiVerdict aiVerdict;

  @Enumerated(EnumType.STRING)
  @Column(length = 20)
  private AiCategory aiCategory;

  /** 0.0–1.0 confidence reported by the classifier. */
  @Column private Double aiConfidence;

  @Column(length = 1000)
  private String aiReason;

  /** Which model/version produced the verdict (e.g. "gemini-2.0-flash"). */
  @Column(length = 100)
  private String aiModel;

  @Column private LocalDateTime evaluatedAt;

  // --- human override ---

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 20)
  @Builder.Default
  private AdminModerationAction adminAction = AdminModerationAction.NONE;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "admin_user_id")
  private UserEntity adminUser;

  @Column(length = 1000)
  private String adminNote;

  @Column private LocalDateTime adminActedAt;
}
