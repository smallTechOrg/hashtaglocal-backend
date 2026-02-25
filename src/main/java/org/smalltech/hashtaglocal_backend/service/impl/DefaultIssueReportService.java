package org.smalltech.hashtaglocal_backend.service.impl;

import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import org.smalltech.hashtaglocal_backend.entity.IssueEntity;
import org.smalltech.hashtaglocal_backend.entity.Location;
import org.smalltech.hashtaglocal_backend.entity.MediaEntity;
import org.smalltech.hashtaglocal_backend.entity.UserEntity;
import org.smalltech.hashtaglocal_backend.model.IssueStatusModel;
import org.smalltech.hashtaglocal_backend.model.IssueTypeModel;
import org.smalltech.hashtaglocal_backend.model.MediaTypeModel;
import org.smalltech.hashtaglocal_backend.model.request.IssueReportRequest;
import org.smalltech.hashtaglocal_backend.model.request.MediaRequest;
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

    // Save media
    if (issueReq.getMediaUrls() != null && !issueReq.getMediaUrls().isEmpty()) {
      for (MediaRequest mediaReq : issueReq.getMediaUrls()) {

        var mediaLocReq = mediaReq.getLocation();

        Location mediaLocation =
            locationService.createAndSaveLocation(
                mediaLocReq.getLat(), mediaLocReq.getLng(), mediaLocReq.getMetaData(), "Unknown");

        MediaEntity media =
            MediaEntity.builder()
                .issue(issue)
                .type(MediaTypeModel.valueOf(mediaReq.getType()))
                .url(mediaReq.getUrl())
                .user(user)
                .location(mediaLocation)
                .createdAt(LocalDateTime.now())
                .build();

        mediaRepository.save(media);
      }
    }

    return issue.getId();
  }
}
