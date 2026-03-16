package org.smalltech.hashtaglocal_backend.service.impl;

import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import org.smalltech.hashtaglocal_backend.entity.IssueActionEntity;
import org.smalltech.hashtaglocal_backend.entity.IssueEntity;
import org.smalltech.hashtaglocal_backend.entity.Location;
import org.smalltech.hashtaglocal_backend.entity.MediaEntity;
import org.smalltech.hashtaglocal_backend.entity.UserEntity;
import org.smalltech.hashtaglocal_backend.model.IssueActionApprovalStatus;
import org.smalltech.hashtaglocal_backend.model.IssueActionModel;
import org.smalltech.hashtaglocal_backend.model.IssueStatusModel;
import org.smalltech.hashtaglocal_backend.model.IssueTypeModel;
import org.smalltech.hashtaglocal_backend.model.MediaTypeModel;
import org.smalltech.hashtaglocal_backend.model.request.IssueReportRequest;
import org.smalltech.hashtaglocal_backend.model.request.MediaRequest;
import org.smalltech.hashtaglocal_backend.repository.IssueActionRepository;
import org.smalltech.hashtaglocal_backend.repository.IssueRepository;
import org.smalltech.hashtaglocal_backend.repository.MediaRepository;
import org.smalltech.hashtaglocal_backend.repository.UserRepository;
import org.smalltech.hashtaglocal_backend.service.IssueReportService;
import org.smalltech.hashtaglocal_backend.service.LocationService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional
public class DefaultIssueReportService implements IssueReportService {

  private final IssueRepository issueRepository;
  private final MediaRepository mediaRepository;
  private final UserRepository userRepository;
  private final LocationService locationService;
  private final IssueActionRepository issueActionRepository;

  @Override
  public Long createIssue(Long userId, IssueReportRequest request) {
    var issueReq = request.getIssue();

    UserEntity user =
        userRepository.findById(userId).orElseThrow(() -> new RuntimeException("User not found"));

    // Save issue location
    Location issueLocation =
        locationService.createAndSaveLocation(
            issueReq.getLocation().getLat(),
            issueReq.getLocation().getLng(),
            issueReq.getLocation().getMetaData(),
            "Unknown");

    // Create issue
    IssueEntity issue =
        IssueEntity.builder()
            .type(IssueTypeModel.valueOf(issueReq.getType()))
            .description(issueReq.getDescription())
            .status(IssueStatusModel.ONHOLD)
            .createdAt(LocalDateTime.now())
            .updatedAt(LocalDateTime.now())
            .location(issueLocation)
            .userEntity(user)
            .build();

    issue = issueRepository.save(issue);

    // Create one REPORT action per media item. The first action carries PENDING approval
    // (governs issue ONHOLD → OPEN transition); additional actions are NOT_REQUIRED (pure
    // media carriers).
    boolean firstMedia = true;
    if (issueReq.getMediaUrls() != null && !issueReq.getMediaUrls().isEmpty()) {
      for (MediaRequest mediaReq : issueReq.getMediaUrls()) {

        MediaEntity media =
            MediaEntity.builder()
                .type(MediaTypeModel.valueOf(mediaReq.getType()))
                .url(mediaReq.getUrl())
                .location(issueLocation)
                .createdAt(LocalDateTime.now())
                .build();

        media = mediaRepository.save(media);

        IssueActionEntity reportAction =
            IssueActionEntity.builder()
                .issueEntity(issue)
                .userEntity(user)
                .action(IssueActionModel.REPORT)
                .approvalStatus(
                    firstMedia
                        ? IssueActionApprovalStatus.PENDING
                        : IssueActionApprovalStatus.NOT_REQUIRED)
                .media(media)
                .createdAt(LocalDateTime.now())
                .build();
        issueActionRepository.save(reportAction);
        firstMedia = false;
      }
    } else {
      // No media — still create a single REPORT action for admin approval
      IssueActionEntity reportAction =
          IssueActionEntity.builder()
              .issueEntity(issue)
              .userEntity(user)
              .action(IssueActionModel.REPORT)
              .approvalStatus(IssueActionApprovalStatus.PENDING)
              .createdAt(LocalDateTime.now())
              .build();
      issueActionRepository.save(reportAction);
    }

    return issue.getId();
  }
}
