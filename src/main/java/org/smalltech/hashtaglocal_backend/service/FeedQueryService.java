package org.smalltech.hashtaglocal_backend.service;

import java.time.LocalDateTime;
import java.util.List;

import org.smalltech.hashtaglocal_backend.entity.FeedPostEntity;
import org.smalltech.hashtaglocal_backend.entity.Locality;
import org.smalltech.hashtaglocal_backend.exception.DownstreamServiceException;
import org.smalltech.hashtaglocal_backend.mapper.FeedPostMapper;
import org.smalltech.hashtaglocal_backend.model.FeedPostStatus;
import org.smalltech.hashtaglocal_backend.model.response.FeedListResponseData;
import org.smalltech.hashtaglocal_backend.model.response.FeedPostData;
import org.smalltech.hashtaglocal_backend.repository.FeedPostRepository;
import org.smalltech.hashtaglocal_backend.repository.LocalityRepository;
import org.smalltech.hashtaglocal_backend.util.FeedCursor;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;

/** Read side of the feed: the public, keyset-paginated timeline. See FEED_DESIGN.md §5. */
@Service
@RequiredArgsConstructor
public class FeedQueryService {

  private static final int DEFAULT_LIMIT = 30;
  private static final int MAX_LIMIT = 100;

  private final FeedPostRepository feedPostRepository;
  private final LocalityRepository localityRepository;
  private final FeedPostMapper feedPostMapper;

  @Transactional(readOnly = true)
  public FeedListResponseData getTimeline(
      String hashtag, String cursorToken, Integer limit, boolean aggregate, Long viewerUserId) {

    Locality locality =
        localityRepository
            .findByHashtagFlexible(hashtag)
            .orElseThrow(
                () ->
                    new DownstreamServiceException(
                        HttpStatus.NOT_FOUND, "NOT_FOUND", "Unknown hashtag: " + hashtag));

    int pageSize = clampLimit(limit);
    FeedCursor cursor = FeedCursor.decode(cursorToken);
    LocalDateTime now = LocalDateTime.now();
    Long id = locality.getId();

    // Fetch one extra row to know whether a further page exists, without a spurious empty page.
    // When aggregate=true (a parent/root hashtag like #india), include all child localities' posts.
    PageRequest page = PageRequest.of(0, pageSize + 1);
    List<FeedPostEntity> rows;
    // The viewer additionally sees their OWN not-yet-published posts (PENDING_AI/FLAGGED/
    // AI_BLOCKED) inline in the timeline — so a user always sees what they just sent, marked
    // "under review" on the client. Everyone else sees PUBLISHED only.
    if (cursor == null) {
      rows =
          aggregate
              ? feedPostRepository.findAggregatedTimelineFirstPage(
                  id, FeedPostStatus.PUBLISHED, now, viewerUserId, page)
              : feedPostRepository.findTimelineFirstPage(
                  id, FeedPostStatus.PUBLISHED, now, viewerUserId, page);
    } else {
      rows =
          aggregate
              ? feedPostRepository.findAggregatedTimelineAfter(
                  id, FeedPostStatus.PUBLISHED, now, viewerUserId, cursor.createdAt(), cursor.id(),
                  page)
              : feedPostRepository.findTimelineAfter(
                  id,
                  FeedPostStatus.PUBLISHED,
                  now,
                  viewerUserId,
                  cursor.createdAt(),
                  cursor.id(),
                  page);
    }

    boolean hasMore = rows.size() > pageSize;
    List<FeedPostEntity> pageRows = hasMore ? rows.subList(0, pageSize) : rows;

    List<FeedPostData> posts =
        pageRows.stream().map(p -> feedPostMapper.toData(p, viewerUserId)).toList();

    String nextCursor = null;
    if (hasMore) {
      FeedPostEntity last = pageRows.get(pageRows.size() - 1);
      nextCursor = new FeedCursor(last.getCreatedAt(), last.getId()).encode();
    }

    // Pinned posts are returned on the first page only.
    List<FeedPostData> pinned =
        cursor != null
            ? List.of()
            : (aggregate
                    ? feedPostRepository.findAggregatedPinned(id, FeedPostStatus.PUBLISHED, now)
                    : feedPostRepository.findPinned(id, FeedPostStatus.PUBLISHED, now))
                .stream().map(p -> feedPostMapper.toData(p, viewerUserId)).toList();

    return FeedListResponseData.builder()
        .pinned(pinned)
        .posts(posts)
        .nextCursor(nextCursor)
        .build();
  }

  @Transactional(readOnly = true)
  public FeedPostData getPost(Long postId, Long viewerUserId) {
    FeedPostEntity post =
        feedPostRepository
            .findById(postId)
            .orElseThrow(
                () ->
                    new DownstreamServiceException(
                        HttpStatus.NOT_FOUND, "NOT_FOUND", "Unknown post: " + postId));

    // Only PUBLISHED posts are public; the author may see their own non-published post.
    boolean visible =
        post.getStatus() == FeedPostStatus.PUBLISHED
            || (viewerUserId != null
                && post.getAuthor() != null
                && viewerUserId.equals(post.getAuthor().getId()));
    if (!visible) {
      throw new DownstreamServiceException(
          HttpStatus.NOT_FOUND, "NOT_FOUND", "Unknown post: " + postId);
    }
    return feedPostMapper.toData(post, viewerUserId);
  }

  private int clampLimit(Integer limit) {
    if (limit == null || limit <= 0) {
      return DEFAULT_LIMIT;
    }
    return Math.min(limit, MAX_LIMIT);
  }
}
