package org.smalltech.hashtaglocal_backend.service;

import java.time.LocalDateTime;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import org.smalltech.hashtaglocal_backend.config.CustomProperties;
import org.smalltech.hashtaglocal_backend.entity.IssueActionEntity;
import org.smalltech.hashtaglocal_backend.model.IssueActionModel;
import org.smalltech.hashtaglocal_backend.model.IssueStatusModel;
import org.smalltech.hashtaglocal_backend.model.request.IssueVerifyRequest;
import org.smalltech.hashtaglocal_backend.repository.IssueActionRepository;
import org.smalltech.hashtaglocal_backend.repository.IssueRepository;
import org.smalltech.hashtaglocal_backend.repository.MediaRepository;
import org.smalltech.hashtaglocal_backend.repository.UserRepository;
import org.smalltech.hashtaglocal_backend.util.EnumParsers;
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

  public Long handle(Long issueId, Long userId, IssueVerifyRequest request) {

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

    // Reject issue: only owner can reject
    if (issueActionModel == IssueActionModel.REJECT) {
      Long ownerId =
          issueEntity.getUserEntity() != null ? issueEntity.getUserEntity().getId() : null;

      if (ownerId == null || !ownerId.equals(userId)) {
        throw new ResponseStatusException(
            HttpStatus.FORBIDDEN, "Only the issue owner can reject the issue");
      }
    }

    // Enforce geo-fence for VERIFY / RESOLVE
    if (issueActionModel == IssueActionModel.VERIFY
        || issueActionModel == IssueActionModel.RESOLVE) {

      var mediaUrls = request.getIssueAction().getMediaUrls();

      if (mediaUrls == null || mediaUrls.isEmpty()) {
        throw new ResponseStatusException(
            HttpStatus.BAD_REQUEST,
            "At least one media with location is required to verify or resolve an issue");
      }

      var actionLocation = mediaUrls.get(0).getLocation();
      if (actionLocation == null
          || actionLocation.getLat() == null
          || actionLocation.getLng() == null) {
        throw new ResponseStatusException(
            HttpStatus.BAD_REQUEST,
            "Location (lat, lng) is required to verify or resolve an issue");
      }

      geoFenceService.assertWithinRadius(
          issueEntity.getLocation(),
          actionLocation.getLat(),
          actionLocation.getLng(),
          appProperties.getGeo().getVerifyRadiusMeters());

      // Process media URLs if provided
      for (var mediaRequest : mediaUrls) {

        var mediaLocReq = mediaRequest.getLocation();

        var mediaLocation =
            locationService.createAndSaveLocation(
                mediaLocReq.getLat(), mediaLocReq.getLng(), mediaLocReq.getMetaData(), "Unknown");

        var mediaEntity =
            org.smalltech.hashtaglocal_backend.entity.MediaEntity.builder()
                .issue(issueEntity)
                .type(EnumParsers.parseMediaType(mediaRequest.getType()))
                .url(mediaRequest.getUrl())
                .user(userEntity)
                .description(mediaRequest.getDescription())
                .location(mediaLocation)
                .createdAt(LocalDateTime.now())
                .build();

        mediaRepository.save(mediaEntity);
      }
    }

    // Save issue action record
    IssueActionEntity issueActionEntity =
        IssueActionEntity.builder()
            .issueEntity(issueEntity)
            .userEntity(userEntity)
            .action(issueActionModel)
            .createdAt(LocalDateTime.now())
            .build();

    issueActionRepository.save(issueActionEntity);

    // Action-based status update
    if (issueActionModel == IssueActionModel.REJECT) {
      issueEntity.setStatus(IssueStatusModel.REJECTED);

    } else if (!IssueStatusModel.ONHOLD.equals(issueEntity.getStatus())) {

      if (issueActionModel == IssueActionModel.VERIFY) {
        issueEntity.setStatus(IssueStatusModel.OPEN);

      } else if (issueActionModel == IssueActionModel.RESOLVE) {
        issueEntity.setStatus(IssueStatusModel.PENDING);
      }
    }

    issueEntity.setUpdatedAt(LocalDateTime.now());
    issueRepository.save(issueEntity);

    return issueId;
  }
}
