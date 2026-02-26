package org.smalltech.hashtaglocal_backend.service;

import lombok.RequiredArgsConstructor;
import org.smalltech.hashtaglocal_backend.entity.IssueEntity;
import org.smalltech.hashtaglocal_backend.model.IssueStatusModel;
import org.smalltech.hashtaglocal_backend.repository.IssueRepository;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class IssueQueryService {

  private final IssueRepository issueRepository;

  /**
   * Fetches an issue by ID without any visibility check. Use only in contexts where the caller is
   * known to be authorised (e.g. admin endpoints or internal services).
   */
  public IssueEntity get(Long issueId) {
    return issueRepository
        .findById(issueId)
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Issue not found"));
  }

  /**
   * Fetches an issue by ID, enforcing owner-only visibility for {@code ONHOLD} issues.
   *
   * <ul>
   *   <li>If the issue is {@code ONHOLD} and {@code viewerUserId} does not match the issue owner, a
   *       {@code 404 Not Found} is returned (to avoid leaking that the issue exists).
   *   <li>Authenticated owners always receive their {@code ONHOLD} issue in full.
   *   <li>All other statuses are publicly accessible.
   * </ul>
   *
   * @param issueId the ID of the issue to fetch
   * @param viewerUserId the authenticated viewer's user ID, or {@code null} for anonymous callers
   */
  public IssueEntity get(Long issueId, Long viewerUserId) {
    IssueEntity issue =
        issueRepository
            .findById(issueId)
            .orElseThrow(
                () -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Issue not found"));

    // REJECTED issues are not visible to anyone — treat as if they don't exist.
    if (IssueStatusModel.REJECTED.equals(issue.getStatus())) {
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Issue not found");
    }

    if (IssueStatusModel.ONHOLD.equals(issue.getStatus())) {
      Long ownerId = issue.getUserEntity() != null ? issue.getUserEntity().getId() : null;
      boolean isOwner = ownerId != null && ownerId.equals(viewerUserId);
      boolean isAdmin = isCurrentUserAdmin();
      if (!isOwner && !isAdmin) {
        // Return 404 rather than 403 to avoid leaking existence of the issue
        throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Issue not found");
      }
    }

    return issue;
  }

  /** Check if the current request is from an ADMIN user via Spring Security context. */
  private boolean isCurrentUserAdmin() {
    Authentication auth = SecurityContextHolder.getContext().getAuthentication();
    return auth != null && auth.getAuthorities().contains(new SimpleGrantedAuthority("ROLE_ADMIN"));
  }
}
