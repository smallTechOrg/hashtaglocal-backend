package org.smalltech.hashtaglocal_backend.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.smalltech.hashtaglocal_backend.entity.FeedPostEntity;
import org.smalltech.hashtaglocal_backend.exception.DownstreamServiceException;
import org.smalltech.hashtaglocal_backend.mapper.FeedPostMapper;
import org.smalltech.hashtaglocal_backend.model.NewAPIResponse;
import org.smalltech.hashtaglocal_backend.model.request.CreateFeedPostRequest;
import org.smalltech.hashtaglocal_backend.model.response.FeedListResponseData;
import org.smalltech.hashtaglocal_backend.model.response.FeedPostData;
import org.smalltech.hashtaglocal_backend.service.FeedQueryService;
import org.smalltech.hashtaglocal_backend.service.FeedService;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

/**
 * Public per-hashtag feed: anonymous reads, authenticated writes. Admin moderation/posting lives in
 * {@link FeedAdminController} under {@code /admin}. See FEED_DESIGN.md §5.
 */
@RestController
@RequestMapping("/api/v1/feed")
@Tag(name = "Feed", description = "Per-hashtag feed / broadcast channel")
@RequiredArgsConstructor
public class FeedController {

  private final FeedQueryService feedQueryService;
  private final FeedService feedService;
  private final FeedPostMapper feedPostMapper;

  @GetMapping
  @Operation(
      summary = "Get the feed timeline for a hashtag",
      description = "Public. Keyset-paginated, newest first. Pinned posts on the first page.")
  public NewAPIResponse<FeedListResponseData> getTimeline(
      @RequestParam String hashtag,
      @RequestParam(required = false) String cursor,
      @RequestParam(required = false) Integer limit,
      @AuthenticationPrincipal Long viewerUserId) {
    return NewAPIResponse.<FeedListResponseData>builder()
        .data(feedQueryService.getTimeline(hashtag, cursor, limit, viewerUserId))
        .build();
  }

  @GetMapping("/{postId}")
  @Operation(summary = "Get a single feed post", description = "Public (PUBLISHED posts only).")
  public NewAPIResponse<FeedPostData> getPost(
      @PathVariable Long postId, @AuthenticationPrincipal Long viewerUserId) {
    return NewAPIResponse.<FeedPostData>builder()
        .data(feedQueryService.getPost(postId, viewerUserId))
        .build();
  }

  @PostMapping
  @Operation(
      summary = "Create a feed post",
      description =
          "Authenticated. USER must send lat/lng (locality resolved server-side) and the post is"
              + " AI-moderated before going public.")
  public NewAPIResponse<FeedPostData> createPost(
      @Valid @RequestBody CreateFeedPostRequest request,
      @AuthenticationPrincipal Long authorUserId) {
    if (authorUserId == null) {
      throw new DownstreamServiceException(
          HttpStatus.UNAUTHORIZED, "AUTH", "Login required to post");
    }
    FeedPostEntity post = feedService.create(authorUserId, request);
    return NewAPIResponse.<FeedPostData>builder()
        .data(feedPostMapper.toData(post, authorUserId))
        .build();
  }
}
