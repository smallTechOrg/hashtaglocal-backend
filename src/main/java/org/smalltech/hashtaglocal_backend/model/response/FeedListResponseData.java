package org.smalltech.hashtaglocal_backend.model.response;

import java.util.List;
import lombok.Builder;
import lombok.Data;

/**
 * Top-level payload for {@code GET /api/v1/feed}. Wrapped in {@code NewAPIResponse}, so the JSON is
 * {@code { "data": { "pinned": [...], "posts": [...], "next_cursor": "..." } }}.
 */
@Data
@Builder
public class FeedListResponseData {

  /** Pinned posts — returned on the first page only (null/empty on subsequent pages). */
  private List<FeedPostData> pinned;

  /** The timeline page, newest first. */
  private List<FeedPostData> posts;

  /** Opaque keyset cursor for the next page; null when there are no more rows. */
  private String nextCursor;
}
