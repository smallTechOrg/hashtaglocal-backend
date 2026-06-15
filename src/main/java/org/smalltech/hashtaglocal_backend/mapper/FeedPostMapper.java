package org.smalltech.hashtaglocal_backend.mapper;

import lombok.RequiredArgsConstructor;
import org.smalltech.hashtaglocal_backend.entity.FeedPostContentEntity;
import org.smalltech.hashtaglocal_backend.entity.FeedPostEntity;
import org.smalltech.hashtaglocal_backend.entity.MediaEntity;
import org.smalltech.hashtaglocal_backend.entity.UserEntity;
import org.smalltech.hashtaglocal_backend.model.response.FeedPostData;
import org.smalltech.hashtaglocal_backend.service.GCSService;
import org.springframework.stereotype.Component;

/**
 * Maps a {@link FeedPostEntity} (+ its {@link FeedPostContentEntity}) to {@link FeedPostData},
 * signing GCS media URLs fresh on each read. See FEED_DESIGN.md §5.
 */
@Component
@RequiredArgsConstructor
public class FeedPostMapper {

  private final GCSService gcsService;
  private final BulletinViewMapper bulletinViewMapper;

  public FeedPostData toData(FeedPostEntity post, Long viewerUserId) {
    FeedPostContentEntity content = post.getContent();

    FeedPostData.FeedPostDataBuilder b =
        FeedPostData.builder()
            .id(post.getId())
            .kind(post.getKind() != null ? post.getKind().name() : null)
            .status(post.getStatus() != null ? post.getStatus().name() : null)
            .hashtag(post.getLocality() != null ? post.getLocality().getHashtag() : null)
            .pinned(post.isPinned())
            .author(toAuthor(post.getAuthor()))
            .createdAt(post.getCreatedAt());

    // Locality centroid → map marker in the aggregated home feed (null for boundary-less roots).
    if (post.getLocality() != null && post.getLocality().getGeoBoundary() != null) {
      org.locationtech.jts.geom.Point c = post.getLocality().getGeoBoundary().getCentroid();
      if (c != null && !c.isEmpty()) {
        b.localityLat(c.getY()).localityLng(c.getX());
      }
    }

    if (content != null) {
      b.text(content.getText())
          .title(content.getTitle())
          .url(content.getUrl())
          .embedHtml(content.getEmbedHtml())
          .embedType(content.getEmbedType() != null ? content.getEmbedType().name() : null)
          .scrapeStatus(content.getScrapeStatus() != null ? content.getScrapeStatus().name() : null)
          .data(content.getData());

      // LINK preview image (re-hosted in GCS)
      b.imageUrl(signedUrlOrNull(content.getImageMedia()));

      // MEDIA item
      MediaEntity media = content.getMedia();
      if (media != null) {
        b.mediaUrl(signedUrlOrNull(media))
            .mediaType(media.getType() != null ? media.getType().name() : null);
      }

      if (content.getIssue() != null) {
        b.issueId(content.getIssue().getId());
      }
      if (content.getEvent() != null) {
        b.eventId(content.getEvent().getId());
      }
      if (content.getBulletin() != null) {
        b.bulletinId(content.getBulletin().getId())
            .bulletin(bulletinViewMapper.toData(content.getBulletin(), viewerUserId));
      }
    }

    boolean isAuthor =
        viewerUserId != null
            && post.getAuthor() != null
            && viewerUserId.equals(post.getAuthor().getId());
    b.viewerContext(
        viewerUserId == null
            ? null
            : FeedPostData.ViewerContext.builder().isAuthor(isAuthor).build());

    return b.build();
  }

  private FeedPostData.AuthorData toAuthor(UserEntity user) {
    if (user == null) {
      return null;
    }
    return FeedPostData.AuthorData.builder()
        .id(user.getId())
        .username(user.getUsername())
        .profilePicture(user.getProfilePicture())
        .build();
  }

  private String signedUrlOrNull(MediaEntity media) {
    return media != null ? gcsService.generateSignedUrl(media.getUrl()) : null;
  }
}
