package org.smalltech.hashtaglocal_backend.service;

import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.smalltech.hashtaglocal_backend.entity.FeedModerationEntity;
import org.smalltech.hashtaglocal_backend.entity.FeedPostEntity;
import org.smalltech.hashtaglocal_backend.entity.UserEntity;
import org.smalltech.hashtaglocal_backend.exception.DownstreamServiceException;
import org.smalltech.hashtaglocal_backend.mapper.FeedPostMapper;
import org.smalltech.hashtaglocal_backend.model.AdminModerationAction;
import org.smalltech.hashtaglocal_backend.model.FeedPostStatus;
import org.smalltech.hashtaglocal_backend.model.response.ModerationQueueData;
import org.smalltech.hashtaglocal_backend.repository.FeedPostRepository;
import org.smalltech.hashtaglocal_backend.repository.UserRepository;
import org.smalltech.hashtaglocal_backend.util.FeedCursor;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Admin moderation actions over the feed. See FEED_DESIGN.md §8. */
@Service
@RequiredArgsConstructor
public class FeedAdminService {

  private static final int DEFAULT_LIMIT = 30;
  private static final int MAX_LIMIT = 100;

  private final FeedPostRepository feedPostRepository;
  private final UserRepository userRepository;
  private final FeedPostMapper feedPostMapper;

  @Transactional(readOnly = true)
  public ModerationQueueData queue(String verdict, String cursorToken, Integer limit) {
    List<FeedPostStatus> statuses =
        switch (verdict == null ? "ALL" : verdict.toUpperCase()) {
          case "BLOCKED" -> List.of(FeedPostStatus.AI_BLOCKED);
          case "FLAGGED" -> List.of(FeedPostStatus.FLAGGED);
          default -> List.of(FeedPostStatus.AI_BLOCKED, FeedPostStatus.FLAGGED);
        };

    int pageSize = clampLimit(limit);
    FeedCursor cursor = FeedCursor.decode(cursorToken);
    List<FeedPostEntity> rows =
        feedPostRepository.findModerationQueue(
            statuses,
            cursor == null ? null : cursor.createdAt(),
            cursor == null ? null : cursor.id(),
            PageRequest.of(0, pageSize));

    List<ModerationQueueData.Item> items = rows.stream().map(this::toItem).toList();
    String nextCursor = null;
    if (rows.size() == pageSize) {
      FeedPostEntity last = rows.get(rows.size() - 1);
      nextCursor = new FeedCursor(last.getCreatedAt(), last.getId()).encode();
    }
    return ModerationQueueData.builder().items(items).nextCursor(nextCursor).build();
  }

  @Transactional
  public void approve(Long postId, Long adminUserId, String note) {
    FeedPostEntity post = requirePost(postId);
    post.setStatus(FeedPostStatus.PUBLISHED);
    if (post.getPublishedAt() == null) {
      post.setPublishedAt(LocalDateTime.now());
    }
    recordAdminAction(post, adminUserId, AdminModerationAction.APPROVED, note);
    feedPostRepository.save(post);
  }

  @Transactional
  public void hide(Long postId, Long adminUserId, String reason) {
    FeedPostEntity post = requirePost(postId);
    post.setStatus(FeedPostStatus.ADMIN_HIDDEN);
    recordAdminAction(post, adminUserId, AdminModerationAction.HIDDEN, reason);
    feedPostRepository.save(post);
  }

  @Transactional
  public void setPinned(Long postId, boolean pinned) {
    FeedPostEntity post = requirePost(postId);
    post.setPinned(pinned);
    feedPostRepository.save(post);
  }

  private void recordAdminAction(
      FeedPostEntity post, Long adminUserId, AdminModerationAction action, String note) {
    UserEntity admin =
        adminUserId == null ? null : userRepository.findById(adminUserId).orElse(null);
    FeedModerationEntity mod = post.getModeration();
    if (mod == null) {
      mod = FeedModerationEntity.builder().post(post).build();
      post.setModeration(mod);
    }
    mod.setAdminAction(action);
    mod.setAdminUser(admin);
    mod.setAdminNote(note);
    mod.setAdminActedAt(LocalDateTime.now());
  }

  private ModerationQueueData.Item toItem(FeedPostEntity post) {
    FeedModerationEntity mod = post.getModeration();
    ModerationQueueData.Item.ItemBuilder b =
        ModerationQueueData.Item.builder().post(feedPostMapper.toData(post, null));
    if (mod != null) {
      b.aiVerdict(mod.getAiVerdict() != null ? mod.getAiVerdict().name() : null)
          .aiCategory(mod.getAiCategory() != null ? mod.getAiCategory().name() : null)
          .aiConfidence(mod.getAiConfidence())
          .aiReason(mod.getAiReason())
          .aiModel(mod.getAiModel())
          .evaluatedAt(mod.getEvaluatedAt())
          .adminAction(mod.getAdminAction() != null ? mod.getAdminAction().name() : null);
    }
    return b.build();
  }

  private FeedPostEntity requirePost(Long postId) {
    return feedPostRepository
        .findById(postId)
        .orElseThrow(
            () ->
                new DownstreamServiceException(
                    HttpStatus.NOT_FOUND, "NOT_FOUND", "Unknown post: " + postId));
  }

  private int clampLimit(Integer limit) {
    if (limit == null || limit <= 0) {
      return DEFAULT_LIMIT;
    }
    return Math.min(limit, MAX_LIMIT);
  }
}
