package org.smalltech.hashtaglocal_backend.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.smalltech.hashtaglocal_backend.entity.FeedPostEntity;
import org.smalltech.hashtaglocal_backend.mapper.FeedPostMapper;
import org.smalltech.hashtaglocal_backend.model.NewAPIResponse;
import org.smalltech.hashtaglocal_backend.model.request.AdminModerationActionRequest;
import org.smalltech.hashtaglocal_backend.model.request.AdminPinRequest;
import org.smalltech.hashtaglocal_backend.model.request.CreateFeedPostRequest;
import org.smalltech.hashtaglocal_backend.model.response.FeedPostData;
import org.smalltech.hashtaglocal_backend.model.response.ModerationQueueData;
import org.smalltech.hashtaglocal_backend.service.FeedAdminService;
import org.smalltech.hashtaglocal_backend.service.FeedBackfillService;
import org.smalltech.hashtaglocal_backend.service.FeedService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

/**
 * Admin feed endpoints. The {@code /admin/**} path is already gated to {@code ROLE_ADMIN} by
 * SecurityConfig (matches {@code IssueAdminController}). See FEED_DESIGN.md §5.1 / §8.
 */
@RestController
@RequestMapping("/admin/feed")
@Tag(name = "Feed Admin", description = "Admin moderation and posting for the feed")
@RequiredArgsConstructor
public class FeedAdminController {

  private final FeedAdminService feedAdminService;
  private final FeedService feedService;
  private final FeedBackfillService feedBackfillService;
  private final FeedPostMapper feedPostMapper;

  @GetMapping("/moderation")
  @Operation(summary = "Moderation review queue (AI-blocked / flagged posts)")
  public NewAPIResponse<ModerationQueueData> moderationQueue(
      @RequestParam(required = false) String verdict,
      @RequestParam(required = false) String cursor,
      @RequestParam(required = false) Integer limit) {
    return NewAPIResponse.<ModerationQueueData>builder()
        .data(feedAdminService.queue(verdict, cursor, limit))
        .build();
  }

  @PostMapping("/{postId}/approve")
  @Operation(summary = "Approve a post (override AI block / flag → PUBLISHED)")
  public NewAPIResponse<Map<String, Object>> approve(
      @PathVariable Long postId,
      @RequestBody(required = false) AdminModerationActionRequest body,
      @AuthenticationPrincipal Long adminUserId) {
    feedAdminService.approve(postId, adminUserId, body == null ? null : body.getNote());
    return NewAPIResponse.<Map<String, Object>>builder()
        .data(Map.of("status", "PUBLISHED"))
        .build();
  }

  @PostMapping("/{postId}/hide")
  @Operation(summary = "Hide a post (→ ADMIN_HIDDEN)")
  public NewAPIResponse<Map<String, Object>> hide(
      @PathVariable Long postId,
      @RequestBody(required = false) AdminModerationActionRequest body,
      @AuthenticationPrincipal Long adminUserId) {
    feedAdminService.hide(postId, adminUserId, body == null ? null : body.getNote());
    return NewAPIResponse.<Map<String, Object>>builder()
        .data(Map.of("status", "ADMIN_HIDDEN"))
        .build();
  }

  @PatchMapping("/{postId}")
  @Operation(summary = "Pin / unpin a post")
  public NewAPIResponse<Map<String, Object>> pin(
      @PathVariable Long postId, @RequestBody AdminPinRequest body) {
    feedAdminService.setPinned(postId, body.isPinned());
    return NewAPIResponse.<Map<String, Object>>builder()
        .data(Map.of("pinned", body.isPinned()))
        .build();
  }

  @DeleteMapping("/{postId}")
  @Operation(
      summary = "Permanently delete a feed post",
      description =
          "Hard delete (removes the post + its content/moderation rows). Use hide for a"
              + " reversible takedown.")
  public NewAPIResponse<Map<String, Object>> delete(@PathVariable Long postId) {
    feedAdminService.deletePost(postId);
    return NewAPIResponse.<Map<String, Object>>builder().data(Map.of("deleted", true)).build();
  }

  @PostMapping
  @Operation(
      summary = "Admin: create a post on any hashtag",
      description = "No location required; posts to the supplied hashtag and publishes directly.")
  public NewAPIResponse<FeedPostData> create(
      @Valid @RequestBody CreateFeedPostRequest request,
      @AuthenticationPrincipal Long adminUserId) {
    FeedPostEntity post = feedService.create(adminUserId, request);
    return NewAPIResponse.<FeedPostData>builder()
        .data(feedPostMapper.toData(post, adminUserId))
        .build();
  }

  @PostMapping("/backfill-issue-refs")
  @Operation(
      summary = "Backfill ISSUE_REF posts for previously-reported issues",
      description =
          "Creates an ISSUE_REF feed post (backdated to the issue's time) for every issue with a"
              + " locality that doesn't already have one. Idempotent — safe to re-run after"
              + " deployment. Optional `max` caps how many to process (0 = all).")
  public NewAPIResponse<Map<String, Object>> backfillIssueRefs(
      @RequestParam(required = false, defaultValue = "0") int max) {
    FeedBackfillService.BackfillResult result = feedBackfillService.backfillIssueRefs(max);
    return NewAPIResponse.<Map<String, Object>>builder()
        .data(
            Map.of(
                "created", result.created(),
                "skipped", result.skipped(),
                "batches", result.batches()))
        .build();
  }
}
