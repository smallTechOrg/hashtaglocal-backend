package org.smalltech.hashtaglocal_backend.service;

import java.time.LocalDateTime;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import org.smalltech.hashtaglocal_backend.config.CustomProperties;
import org.smalltech.hashtaglocal_backend.entity.IssueActionEntity;
import org.smalltech.hashtaglocal_backend.event.IssueActionPendingEvent;
import org.smalltech.hashtaglocal_backend.model.IssueActionApprovalStatus;
import org.smalltech.hashtaglocal_backend.model.IssueActionModel;
import org.smalltech.hashtaglocal_backend.model.IssueActionResult;
import org.smalltech.hashtaglocal_backend.model.IssueStatusModel;
import org.smalltech.hashtaglocal_backend.model.UserRole;
import org.smalltech.hashtaglocal_backend.model.request.IssueVerifyRequest;
import org.smalltech.hashtaglocal_backend.repository.IssueActionRepository;
import org.smalltech.hashtaglocal_backend.repository.IssueRepository;
import org.smalltech.hashtaglocal_backend.repository.MediaRepository;
import org.smalltech.hashtaglocal_backend.repository.UserRepository;
import org.smalltech.hashtaglocal_backend.util.EnumParsers;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@RequiredArgsConstructor
@Transactional
public class IssueActionService {

  private final CustomProperties.App appProperties;
  private final IssueActionRepository issueActionRepository;
  private final IssueRepository issueRepository;
  private final MediaRepository mediaRepository;
  private final UserRepository userRepository;
  private final GeoFenceService geoFenceService;
  private final LocationService locationService;
  private final ApplicationEventPublisher eventPublisher;

  public IssueActionResult handle(Long issueId, Long userId, IssueVerifyRequest request) {

    if (request == null || request.getIssueAction() == null) {
      throw new ResponseStatusException(
          HttpStatus.BAD_REQUEST, "Request body with issueAction is required");
    }

    String action = request.getIssueAction().getAction();

    IssueActionModel issueActionModel;
    try {
      issueActionModel = IssueActionModel.valueOf(action.toUpperCase(Locale.ROOT));
    } catch (Exception e) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid action:" + action);
    }

    var issueEntity =
        issueRepository
            .findById(issueId)
            .orElseThrow(
                () -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Issue not found"));

    if (userId == null) {
      throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "User not authenticated");
    }

    var userEntity =
        userRepository
            .findById(userId)
            .orElseThrow(
                () ->
                    new ResponseStatusException(
                        HttpStatus.INTERNAL_SERVER_ERROR, "User not found"));

    // APPROVE is reserved for the admin endpoint — block public callers
    if (issueActionModel == IssueActionModel.APPROVE) {
      throw new ResponseStatusException(HttpStatus.FORBIDDEN, "APPROVE is an admin-only action");
    }

    // Reject issue: only owner can reject
    if (issueActionModel == IssueActionModel.REJECT) {
      Long ownerId =
          issueEntity.getUserEntity() != null ? issueEntity.getUserEntity().getId() : null;
      boolean isAdmin = UserRole.ADMIN.equals(userEntity.getRole());

      if (!isAdmin && (ownerId == null || !ownerId.equals(userId))) {
        throw new ResponseStatusException(
            HttpStatus.FORBIDDEN, "Only the issue owner or an admin can reject the issue");
      }
    }

    // Enforce geo-fence for VERIFY / RESOLVE (geo-fence is skipped when media location is
    // absent, preserving backward compatibility with clients that do not send coordinates).
    if (issueActionModel == IssueActionModel.VERIFY
        || issueActionModel == IssueActionModel.RESOLVE) {

      var mediaUrls = request.getIssueAction().getMediaUrls();

      // Geo-fence: only enforced when at least one media item carries a location.
      if (mediaUrls != null && !mediaUrls.isEmpty()) {
        var firstLocation = mediaUrls.get(0).getLocation();
        if (firstLocation != null
            && firstLocation.getLat() != null
            && firstLocation.getLng() != null) {
          geoFenceService.assertWithinRadius(
              issueEntity.getLocation(),
              firstLocation.getLat(),
              firstLocation.getLng(),
              appProperties.getGeo().getVerifyRadiusMeters());
        }
      }

      // Create one action per media item (media_urls is optional — old clients may omit it).
      if (mediaUrls != null) {
        for (var mediaRequest : mediaUrls) {

          var mediaLocReq = mediaRequest.getLocation();

          var mediaLocation =
              mediaLocReq != null && mediaLocReq.getLat() != null && mediaLocReq.getLng() != null
                  ? locationService.createAndSaveLocation(
                      mediaLocReq.getLat(),
                      mediaLocReq.getLng(),
                      mediaLocReq.getMetaData(),
                      "Unknown")
                  : null;

          // Media submitted as part of a VERIFY/RESOLVE action; visibility is controlled
          // by the parent action's approvalStatus (starts as PENDING until admin approves).
          var mediaEntity =
              org.smalltech.hashtaglocal_backend.entity.MediaEntity.builder()
                  .type(EnumParsers.parseMediaType(mediaRequest.getType()))
                  .url(mediaRequest.getUrl())
                  .description(mediaRequest.getDescription())
                  .location(mediaLocation)
                  .createdAt(LocalDateTime.now())
                  .build();

          var savedMedia = mediaRepository.save(mediaEntity);

          IssueActionEntity actionEntity =
              IssueActionEntity.builder()
                  .issueEntity(issueEntity)
                  .userEntity(userEntity)
                  .action(issueActionModel)
                  .approvalStatus(IssueActionApprovalStatus.PENDING)
                  .media(savedMedia)
                  .createdAt(LocalDateTime.now())
                  .build();
          issueActionRepository.save(actionEntity);
        }
      }

      // If no media was submitted at all, still record the action (no media link).
      if (mediaUrls == null || mediaUrls.isEmpty()) {
        IssueActionEntity actionEntity =
            IssueActionEntity.builder()
                .issueEntity(issueEntity)
                .userEntity(userEntity)
                .action(issueActionModel)
                .approvalStatus(IssueActionApprovalStatus.PENDING)
                .createdAt(LocalDateTime.now())
                .build();
        issueActionRepository.save(actionEntity);
      }
    } else {
      // Non-media actions (REJECT, UPDATE): single action, no media
      IssueActionApprovalStatus approvalStatus =
          (issueActionModel == IssueActionModel.REJECT
                  || issueActionModel == IssueActionModel.UPDATE)
              ? IssueActionApprovalStatus.NOT_REQUIRED
              : IssueActionApprovalStatus.PENDING;

      IssueActionEntity issueActionEntity =
          IssueActionEntity.builder()
              .issueEntity(issueEntity)
              .userEntity(userEntity)
              .action(issueActionModel)
              .approvalStatus(approvalStatus)
              .createdAt(LocalDateTime.now())
              .build();
      issueActionRepository.save(issueActionEntity);
    }

    // VERIFY/RESOLVE land in the admin review queue (PENDING) — alert ops.
    if (issueActionModel == IssueActionModel.VERIFY
        || issueActionModel == IssueActionModel.RESOLVE) {
      eventPublisher.publishEvent(new IssueActionPendingEvent(issueId, issueActionModel, userId));
    }

    // Action-based status update
    // REJECT: owner withdraws the issue immediately
    // RESOLVE: sets PENDING so admin sees it in the approval queue
    // VERIFY: no status change — status is driven entirely by admin REPORT approval
    if (issueActionModel == IssueActionModel.REJECT) {
      issueEntity.setStatus(IssueStatusModel.REJECTED);

    } else if (issueActionModel == IssueActionModel.RESOLVE
        && !IssueStatusModel.ONHOLD.equals(issueEntity.getStatus())) {
      issueEntity.setStatus(IssueStatusModel.PENDING);
    }

    issueEntity.setUpdatedAt(LocalDateTime.now());
    issueRepository.save(issueEntity);

    return IssueActionResult.builder().issueId(issueId).karmaAwarded(0).build();
  }
}
