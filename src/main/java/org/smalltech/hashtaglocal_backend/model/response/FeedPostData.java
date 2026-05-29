package org.smalltech.hashtaglocal_backend.model.response;

import java.time.LocalDateTime;
import java.util.Map;
import lombok.Builder;
import lombok.Data;

/**
 * A single feed post in API responses (serialised snake_case). Assembled from {@code feed_posts} +
 * {@code feed_post_content}. Always carries the common fields; the content fields meaningful for
 * the post's {@code kind} are populated, the rest are null. See FEED_DESIGN.md §5.
 */
@Data
@Builder
public class FeedPostData {

  private Long id;

  /** Enum name of the {@code FeedPostKind}. */
  private String kind;

  /** Enum name of the {@code FeedPostStatus}. Mainly relevant to the author/admin. */
  private String status;

  private String hashtag;
  private boolean pinned;

  private AuthorData author;
  private String text;
  private LocalDateTime createdAt;

  // --- LINK ---
  private String url;
  private String title;
  private String imageUrl;
  private String embedHtml;
  private String embedType;

  /** Enum name of the {@code LinkScrapeStatus} for LINK posts; null otherwise. */
  private String scrapeStatus;

  // --- MEDIA ---
  private String mediaUrl;
  private String mediaType;

  // --- references ---
  private Long issueId;
  private Long eventId;

  /** Type-specific tail (poll options, quiz questions, etc.). */
  private Map<String, Object> data;

  /** Viewer-specific context; populated only when the request is authenticated. */
  private ViewerContext viewerContext;

  @Data
  @Builder
  public static class AuthorData {
    private Long id;
    private String username;
    private String profilePicture;
  }

  @Data
  @Builder
  public static class ViewerContext {
    /** Whether the viewer authored this post. */
    private boolean isAuthor;

    private boolean voted;
    private boolean answered;
  }
}
