package org.smalltech.hashtaglocal_backend.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import lombok.*;
import org.smalltech.hashtaglocal_backend.model.LinkEmbedType;

/**
 * A cache of scraped link-preview metadata keyed by canonical URL, so a shared link is scraped once
 * and reused across posts/hashtags. This is a <em>cache</em>, not a per-post table — feed posts
 * hold their own copy of the preview on {@link FeedPostContentEntity}. See FEED_DESIGN.md §6.
 */
@Entity
@Table(name = "link_cache")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LinkCache {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(nullable = false, unique = true, length = 5000)
  private String canonicalUrl;

  @Column(length = 500)
  private String title;

  @Column(length = 2000)
  private String description;

  @Column(length = 300)
  private String siteName;

  /**
   * Re-hosted Open Graph image (GCS); CDN source URLs expire, mirroring {@code EventEntity.media}.
   */
  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "image_media_id")
  private MediaEntity imageMedia;

  @Column(length = 2000)
  private String faviconUrl;

  @Column(columnDefinition = "text")
  private String embedHtml;

  @Enumerated(EnumType.STRING)
  @Column(length = 10)
  private LinkEmbedType embedType;

  @Column(nullable = false, updatable = false)
  private LocalDateTime createdAt;

  @PrePersist
  protected void onCreate() {
    if (createdAt == null) {
      createdAt = LocalDateTime.now();
    }
  }
}
