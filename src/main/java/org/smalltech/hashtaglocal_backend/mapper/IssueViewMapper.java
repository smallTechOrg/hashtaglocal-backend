package org.smalltech.hashtaglocal_backend.mapper;

import java.util.List;
import lombok.RequiredArgsConstructor;
import org.smalltech.hashtaglocal_backend.entity.IssueActionEntity;
import org.smalltech.hashtaglocal_backend.entity.IssueEntity;
import org.smalltech.hashtaglocal_backend.entity.MediaEntity;
import org.smalltech.hashtaglocal_backend.entity.UserEntity;
import org.smalltech.hashtaglocal_backend.model.GovPortalData;
import org.smalltech.hashtaglocal_backend.model.Issue;
import org.smalltech.hashtaglocal_backend.model.IssueActionApprovalStatus;
import org.smalltech.hashtaglocal_backend.model.IssueActionModel;
import org.smalltech.hashtaglocal_backend.model.Locality;
import org.smalltech.hashtaglocal_backend.model.Location;
import org.smalltech.hashtaglocal_backend.model.Media;
import org.smalltech.hashtaglocal_backend.model.User;
import org.smalltech.hashtaglocal_backend.model.ViewerContext;
import org.smalltech.hashtaglocal_backend.model.response.IssueResponseData;
import org.smalltech.hashtaglocal_backend.repository.GovPortalRepository;
import org.smalltech.hashtaglocal_backend.repository.IssueActionRepository;
import org.smalltech.hashtaglocal_backend.service.GCSService;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

/**
 * Maps an {@link IssueEntity} to its API response representation.
 *
 * <h2>Media visibility rules</h2>
 *
 * <ul>
 *   <li><b>Issue owner or admin</b>: receives media from actions with {@code APPROVED}, {@code
 *       NOT_REQUIRED} or {@code PENDING} approval status. Owners see their own pending uploads;
 *       admins need to see pending media during review.
 *   <li><b>Everyone else</b>: receives media only from {@code APPROVED} or {@code NOT_REQUIRED}
 *       actions.
 * </ul>
 *
 * <h2>verifyCount</h2>
 *
 * Only counts VERIFY actions that have been admin-approved. Pending or rejected verifications do
 * not contribute to the count.
 */
@Component
@RequiredArgsConstructor
public class IssueViewMapper {

  private final GovPortalRepository govPortalRepository;
  private final IssueActionRepository issueActionRepository;
  private final GCSService gcsService;

  /** Maps an issue for an anonymous / unauthenticated viewer. Only approved media is included. */
  public IssueResponseData map(IssueEntity entity) {
    return map(entity, null);
  }

  /**
   * Maps an issue for a specific viewer, applying owner-aware media visibility.
   *
   * @param entity the issue to map
   * @param viewerUserId authenticated viewer's user ID, or {@code null} for anonymous callers
   */
  public IssueResponseData map(IssueEntity entity, Long viewerUserId) {
    // Get user from entity - IssueDataInitializer ensures all issues have user ID 1
    UserEntity userEntity = entity.getUserEntity();
    if (userEntity == null) {
      userEntity = new UserEntity();
      userEntity.setUsername("admin");
      userEntity.setProfilePicture("https://example.com/default-profile.jpg");
    }

    User user =
        User.builder()
            .username(userEntity.getUsername())
            .profilePhoto(userEntity.getProfilePicture())
            .build();

    // Map Locality from Location entity with robust null-safety
    org.smalltech.hashtaglocal_backend.entity.Location locEntity = entity.getLocation();
    String hashtag = "world";
    if (locEntity != null
        && locEntity.getLocality() != null
        && locEntity.getLocality().getHashtag() != null) {
      hashtag = locEntity.getLocality().getHashtag();
    }
    Locality locality = Locality.builder().hashtags(List.of(hashtag)).build();

    double lat = 0.0;
    double lng = 0.0;
    String name = "Unknown";
    if (locEntity != null) {
      if (locEntity.getPoint() != null) {
        lat = locEntity.getPoint().getY();
        lng = locEntity.getPoint().getX();
      }
      if (locEntity.getName() != null) {
        name = locEntity.getName();
      }
    }

    Location location =
        Location.builder()
            .lat(lat)
            .lng(lng)
            .locality(locality)
            .address(name)
            .colloquialName(name)
            .build();

    // Determine whether the viewer is the issue owner or an admin
    Long ownerId = userEntity.getId();
    boolean isOwner = viewerUserId != null && viewerUserId.equals(ownerId);
    boolean isAdmin = isCurrentUserAdmin();

    // Fetch media through actions based on viewer identity:
    //   owner or admin → APPROVED + NOT_REQUIRED + PENDING (admins need to see pending media to
    //                     review; owners see their own pending uploads)
    //   others         → APPROVED + NOT_REQUIRED only
    List<IssueActionApprovalStatus> approvalStatuses =
        (isOwner || isAdmin)
            ? List.of(
                IssueActionApprovalStatus.APPROVED,
                IssueActionApprovalStatus.NOT_REQUIRED,
                IssueActionApprovalStatus.PENDING)
            : List.of(IssueActionApprovalStatus.APPROVED, IssueActionApprovalStatus.NOT_REQUIRED);

    List<IssueActionEntity> actionsWithMedia =
        issueActionRepository.findByIssueWithMediaAndApprovalStatusIn(entity, approvalStatuses);

    List<Media> mediaList =
        actionsWithMedia.stream()
            .map(
                action -> {
                  MediaEntity mediaEntity = action.getMedia();
                  // Derive username from the action's user (media has no user FK)
                  String username = "admin";
                  if (action.getUserEntity() != null
                      && action.getUserEntity().getUsername() != null) {
                    username = action.getUserEntity().getUsername();
                  }

                  double mediaLocLat = 0.0;
                  double mediaLocLng = 0.0;
                  String mediaLocName = "Unknown";

                  if (mediaEntity.getLocation() != null) {
                    if (mediaEntity.getLocation().getPoint() != null) {
                      mediaLocLat = mediaEntity.getLocation().getPoint().getY();
                      mediaLocLng = mediaEntity.getLocation().getPoint().getX();
                    }
                    if (mediaEntity.getLocation().getName() != null) {
                      mediaLocName = mediaEntity.getLocation().getName();
                    }
                  }

                  Location mediaLocation =
                      Location.builder()
                          .lat(mediaLocLat)
                          .lng(mediaLocLng)
                          .locality(locality)
                          .address(mediaLocName)
                          .colloquialName(mediaLocName)
                          .build();

                  return Media.builder()
                      .location(mediaLocation)
                      .type(mediaEntity.getType().name().toLowerCase())
                      .url(gcsService.generateOptimisedUrl(mediaEntity.getUrl()))
                      .urlThumbnail(gcsService.generateThumbnailUrl(mediaEntity.getUrl()))
                      .description(mediaEntity.getDescription())
                      .username(username)
                      .profilePhoto(
                          action.getUserEntity() != null
                              ? action.getUserEntity().getProfilePicture()
                              : null)
                      .createdAt(mediaEntity.getCreatedAt())
                      .build();
                })
            .toList();

    List<GovPortalData> govPortalData =
        govPortalRepository.findByIssueEntityId(entity.getId()).stream()
            .map(
                portal ->
                    GovPortalData.builder()
                        .trackingId(portal.getTrackingId())
                        .status(portal.getStatus())
                        .portalName(portal.getPortal().name())
                        .portalTrackLink(portal.getUrl())
                        .metaData(portal.getMetaData())
                        .createdAt(portal.getCreatedAt())
                        .updatedAt(portal.getUpdatedAt())
                        .build())
            .toList();

    int verifyCount =
        issueActionRepository.countDistinctUserByIssueAndAction(entity, IssueActionModel.VERIFY);

    ViewerContext viewerContext = ViewerContext.builder().upvote(false).isOwner(isOwner).build();

    Issue issue =
        Issue.builder()
            .id(entity.getId())
            .user(user)
            .location(location)
            .type(entity.getType().name().toLowerCase())
            .description(entity.getDescription())
            .createdAt(entity.getCreatedAt())
            .mediaUrls(mediaList)
            .govPortalData(govPortalData)
            .voteCount(0)
            .verifyCount(verifyCount)
            .status(entity.getStatus().name())
            .rank(1)
            .viewerContext(viewerContext)
            .build();

    return IssueResponseData.builder().issue(issue).build();
  }

  /** Check whether the current authenticated user has the ADMIN role. */
  private boolean isCurrentUserAdmin() {
    Authentication auth = SecurityContextHolder.getContext().getAuthentication();
    return auth != null && auth.getAuthorities().contains(new SimpleGrantedAuthority("ROLE_ADMIN"));
  }
}
