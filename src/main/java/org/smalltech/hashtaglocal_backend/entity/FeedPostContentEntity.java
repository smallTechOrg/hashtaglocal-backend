package org.smalltech.hashtaglocal_backend.entity;

import com.vladmihalcea.hibernate.type.json.JsonType;
import jakarta.persistence.*;
import java.util.Map;
import lombok.*;
import org.hibernate.annotations.Type;
import org.smalltech.hashtaglocal_backend.model.LinkEmbedType;
import org.smalltech.hashtaglocal_backend.model.LinkScrapeStatus;

/**
 * The universal content of a feed post — one row per {@link FeedPostEntity}, sharing its primary
 * key ({@code @MapsId}). This single table replaces the per-type tables: it carries FK references
 * to entities that already have their own tables ({@code issue}, {@code event}, {@code media}), a
 * small fixed set of generic columns reused across kinds, and a {@code data} JSONB tail for the
 * type-specific bits (poll options, quiz questions, OG extras). See FEED_DESIGN.md §4.2.
 *
 * <p>Adding a new content kind means a new {@code FeedPostKind} + new {@code data} keys — no new
 * table or migration.
 */
@Entity
@Table(name = "feed_post_content")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class FeedPostContentEntity {

  /** Shared PK/FK with {@link FeedPostEntity}. */
  @Id private Long feedPostId;

  @OneToOne(fetch = FetchType.LAZY)
  @MapsId
  @JoinColumn(name = "feed_post_id")
  private FeedPostEntity post;

  // --- references to entities that already have their own tables (no copy) ---

  /** For {@code ISSUE_REF}. */
  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "issue_id")
  private IssueEntity issue;

  /** For {@code EVENT_REF}. */
  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "event_id")
  private EventEntity event;

  /** The single media item for a {@code MEDIA} post (reuses the existing {@link MediaEntity}). */
  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "media_id")
  private MediaEntity media;

  /**
   * A re-hosted preview image, e.g. the Open Graph image of a {@code LINK} (also a MediaEntity).
   */
  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "image_media_id")
  private MediaEntity imageMedia;

  // --- generic structured columns reused across kinds ---

  @Column(length = 500)
  private String title;

  /** Post body / poll question / etc. */
  @Column(length = 4000)
  private String text;

  /** Shared link (LINK). */
  @Column(length = 5000)
  private String url;

  /** Normalized URL, for dedupe / link-cache lookup. */
  @Column(length = 5000)
  private String canonicalUrl;

  /** oEmbed HTML (LINK). */
  @Column(columnDefinition = "text")
  private String embedHtml;

  @Enumerated(EnumType.STRING)
  @Column(length = 10)
  private LinkEmbedType embedType;

  /** Scrape lifecycle for a LINK post; {@code null} for non-link kinds. */
  @Enumerated(EnumType.STRING)
  @Column(length = 10)
  private LinkScrapeStatus scrapeStatus;

  // --- type-specific tail ---

  /** Poll options, quiz questions/options, OG extras, etc. */
  @Type(JsonType.class)
  @Column(columnDefinition = "jsonb")
  private Map<String, Object> data;
}
