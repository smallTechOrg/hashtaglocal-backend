package org.smalltech.hashtaglocal_backend.model.response;

import java.time.LocalDateTime;
import java.util.List;
import lombok.Builder;
import lombok.Data;

/**
 * Admin moderation review queue payload ({@code GET /admin/feed/moderation}). Each item is the post
 * plus its AI verdict so an admin can decide to approve or hide. See FEED_DESIGN.md §8.
 */
@Data
@Builder
public class ModerationQueueData {

  private List<Item> items;
  private String nextCursor;

  @Data
  @Builder
  public static class Item {
    private FeedPostData post;
    private String aiVerdict;
    private String aiCategory;
    private Double aiConfidence;
    private String aiReason;
    private String aiModel;
    private LocalDateTime evaluatedAt;
    private String adminAction;
  }
}
