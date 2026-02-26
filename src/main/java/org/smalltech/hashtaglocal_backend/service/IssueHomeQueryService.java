package org.smalltech.hashtaglocal_backend.service;

import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.smalltech.hashtaglocal_backend.entity.IssueEntity;
import org.smalltech.hashtaglocal_backend.model.IssueStatusModel;
import org.smalltech.hashtaglocal_backend.repository.IssueRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class IssueHomeQueryService {

  private final IssueRepository issueRepository;

  /**
   * Fetches recent issues for the feed. ONHOLD issues are included only when they belong to the
   * authenticated viewer (so the reporter can see their own pending issues).
   *
   * @param localityHashtag optional locality filter
   * @param viewerUserId authenticated viewer's user ID, or {@code null} for anonymous callers
   */
  public List<IssueEntity> findRecentIssues(String localityHashtag, Long viewerUserId) {
    LocalDateTime since = LocalDateTime.now().minusMonths(6);
    Long safeUserId = viewerUserId != null ? viewerUserId : -1L;

    List<IssueStatusModel> statuses =
        List.of(IssueStatusModel.OPEN, IssueStatusModel.PENDING, IssueStatusModel.RESOLVED);

    if (localityHashtag != null && !localityHashtag.isBlank()) {
      return issueRepository
          .findByStatusInOrOnholdOwnedAndCreatedAtAfterAndLocalityHashtagOrderByCreatedAtDesc(
              statuses, safeUserId, since, localityHashtag);
    }

    return issueRepository.findByStatusInOrOnholdOwnedAndCreatedAtAfterOrderByCreatedAtDesc(
        statuses, safeUserId, since);
  }

  /**
   * Fetches nearby issues for the map. ONHOLD issues are included only when they belong to the
   * authenticated viewer.
   *
   * @param viewerUserId authenticated viewer's user ID, or {@code null} for anonymous callers
   */
  public List<IssueEntity> findNearbyIssues(
      double lat, double lng, double radiusMeters, Long viewerUserId) {
    LocalDateTime since = LocalDateTime.now().minusMonths(6);
    Long safeUserId = viewerUserId != null ? viewerUserId : -1L;

    List<String> statuses =
        List.of(
            IssueStatusModel.OPEN.name(),
            IssueStatusModel.PENDING.name(),
            IssueStatusModel.RESOLVED.name());

    return issueRepository.findByStatusInOrOnholdOwnedAndCreatedAtAfterAndWithinRadius(
        statuses, safeUserId, since, lat, lng, radiusMeters);
  }
}
