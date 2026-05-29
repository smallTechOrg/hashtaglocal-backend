package org.smalltech.hashtaglocal_backend.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.smalltech.hashtaglocal_backend.entity.*;
import org.smalltech.hashtaglocal_backend.event.FeedPostCreatedEvent;
import org.smalltech.hashtaglocal_backend.exception.DownstreamServiceException;
import org.smalltech.hashtaglocal_backend.model.FeedPostKind;
import org.smalltech.hashtaglocal_backend.model.FeedPostStatus;
import org.smalltech.hashtaglocal_backend.model.LinkScrapeStatus;
import org.smalltech.hashtaglocal_backend.model.UserRole;
import org.smalltech.hashtaglocal_backend.model.request.CreateFeedPostRequest;
import org.smalltech.hashtaglocal_backend.repository.*;
import org.smalltech.hashtaglocal_backend.util.LinkUrls;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Write side of the feed: creating posts. Role-based locality resolution and the AI-gate decision
 * live here (not the controller), so every caller inherits them. See FEED_DESIGN.md §5.1 / §8.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class FeedService {

  private final FeedPostRepository feedPostRepository;
  private final LocalityRepository localityRepository;
  private final UserRepository userRepository;
  private final MediaRepository mediaRepository;
  private final IssueRepository issueRepository;
  private final EventRepository eventRepository;
  private final ApplicationEventPublisher eventPublisher;

  /**
   * Create a feed post on behalf of {@code authorUserId} (must be a real, authenticated user). USER
   * posts resolve their locality from coordinates and go through AI moderation; ADMIN posts target
   * a hashtag directly and publish immediately.
   */
  @Transactional
  public FeedPostEntity create(Long authorUserId, CreateFeedPostRequest req) {
    UserEntity author =
        userRepository
            .findById(authorUserId)
            .orElseThrow(
                () ->
                    new DownstreamServiceException(
                        HttpStatus.UNAUTHORIZED, "AUTH", "Authenticated user not found"));

    boolean isAdmin = author.getRole() == UserRole.ADMIN;
    Locality locality = resolveLocality(author, req, isAdmin);

    FeedPostEntity post =
        FeedPostEntity.builder()
            .locality(locality)
            .author(author)
            .kind(req.getKind())
            // ADMIN (trusted) publishes directly; USER posts await AI moderation.
            .status(isAdmin ? FeedPostStatus.PUBLISHED : FeedPostStatus.PENDING_AI)
            .publishedAt(isAdmin ? java.time.LocalDateTime.now() : null)
            .build();

    FeedPostContentEntity content = buildContent(post, req);
    post.setContent(content);

    FeedPostEntity saved = feedPostRepository.save(post);

    // Trigger async side-effects only AFTER this transaction commits (handled by
    // FeedPostSideEffectListener), so the worker thread can never read the row before it exists.
    boolean needsScrape = req.getKind() == FeedPostKind.LINK && req.getLinkUrl() != null;
    boolean needsModeration = !isAdmin;
    if (needsScrape || needsModeration) {
      eventPublisher.publishEvent(
          new FeedPostCreatedEvent(saved.getId(), needsScrape, needsModeration));
    }
    return saved;
  }

  /**
   * Create a system-authored post (no human author, no AI gate) — e.g. the auto issue-ref.
   * Publishes directly. See FEED_DESIGN.md §1.2 / §8.
   */
  @Transactional
  public FeedPostEntity createSystemPost(
      Locality locality, FeedPostKind kind, FeedPostContentEntity content) {
    FeedPostEntity post =
        FeedPostEntity.builder()
            .locality(locality)
            .author(null)
            .kind(kind)
            .status(FeedPostStatus.PUBLISHED)
            .publishedAt(java.time.LocalDateTime.now())
            .build();
    content.setPost(post);
    post.setContent(content);
    return feedPostRepository.save(post);
  }

  private Locality resolveLocality(UserEntity author, CreateFeedPostRequest req, boolean isAdmin) {
    boolean hasHashtag = req.getHashtag() != null && !req.getHashtag().isBlank();

    // Posting to an explicit hashtag (without being there) is an admin-only broadcast.
    if (hasHashtag) {
      if (!isAdmin) {
        throw new DownstreamServiceException(
            HttpStatus.FORBIDDEN,
            "PERMISSION",
            "Only admins may post to a hashtag directly; regular posts use your location.");
      }
      return localityRepository
          .findByHashtagFlexible(req.getHashtag())
          .orElseThrow(
              () ->
                  new DownstreamServiceException(
                      HttpStatus.NOT_FOUND, "NOT_FOUND", "Unknown hashtag: " + req.getHashtag()));
    }

    // Otherwise (any role) resolve the locality from coordinates via point-in-polygon. This lets an
    // admin posting through the regular composer behave like a normal located post.
    if (req.getLat() == null || req.getLng() == null) {
      throw new DownstreamServiceException(
          HttpStatus.UNPROCESSABLE_ENTITY,
          "GEO",
          "Location is required to post. Enable location and try again.");
    }
    return localityRepository
        .findContainingLocality(req.getLat(), req.getLng())
        .orElseThrow(
            () ->
                new DownstreamServiceException(
                    HttpStatus.UNPROCESSABLE_ENTITY,
                    "GEO",
                    "Your location is not inside any supported hashtag area."));
  }

  private FeedPostContentEntity buildContent(FeedPostEntity post, CreateFeedPostRequest req) {
    FeedPostContentEntity.FeedPostContentEntityBuilder c =
        FeedPostContentEntity.builder().post(post).text(req.getText());

    switch (req.getKind()) {
      case LINK -> {
        if (req.getLinkUrl() == null || req.getLinkUrl().isBlank()) {
          throw new DownstreamServiceException(
              HttpStatus.BAD_REQUEST, "VALIDATION", "link_url is required for LINK posts");
        }
        c.url(req.getLinkUrl())
            .canonicalUrl(LinkUrls.canonicalize(req.getLinkUrl()))
            .scrapeStatus(LinkScrapeStatus.PENDING);
      }
      case MEDIA -> c.media(requireMedia(req.getMediaId()));
      case ISSUE_REF -> c.issue(requireIssue(req.getIssueId()));
      case EVENT_REF -> c.event(requireEvent(req.getEventId()));
      case TEXT -> {
        if (req.getText() == null || req.getText().isBlank()) {
          throw new DownstreamServiceException(
              HttpStatus.BAD_REQUEST, "VALIDATION", "text is required for TEXT posts");
        }
      }
      default ->
          throw new DownstreamServiceException(
              HttpStatus.BAD_REQUEST, "VALIDATION", "Unsupported kind for v1: " + req.getKind());
    }
    return c.build();
  }

  private MediaEntity requireMedia(Long id) {
    if (id == null) {
      throw new DownstreamServiceException(
          HttpStatus.BAD_REQUEST, "VALIDATION", "media_id is required for MEDIA posts");
    }
    return mediaRepository
        .findById(id)
        .orElseThrow(
            () ->
                new DownstreamServiceException(
                    HttpStatus.NOT_FOUND, "NOT_FOUND", "Unknown media_id: " + id));
  }

  private IssueEntity requireIssue(Long id) {
    if (id == null) {
      throw new DownstreamServiceException(
          HttpStatus.BAD_REQUEST, "VALIDATION", "issue_id is required for ISSUE_REF posts");
    }
    return issueRepository
        .findById(id)
        .orElseThrow(
            () ->
                new DownstreamServiceException(
                    HttpStatus.NOT_FOUND, "NOT_FOUND", "Unknown issue_id: " + id));
  }

  private EventEntity requireEvent(Long id) {
    if (id == null) {
      throw new DownstreamServiceException(
          HttpStatus.BAD_REQUEST, "VALIDATION", "event_id is required for EVENT_REF posts");
    }
    return eventRepository
        .findById(id)
        .orElseThrow(
            () ->
                new DownstreamServiceException(
                    HttpStatus.NOT_FOUND, "NOT_FOUND", "Unknown event_id: " + id));
  }
}
