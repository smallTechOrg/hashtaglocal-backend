package org.smalltech.hashtaglocal_backend.service;

import java.util.Optional;
import org.smalltech.hashtaglocal_backend.entity.UserAuthSessionEntity;
import org.smalltech.hashtaglocal_backend.entity.UserEntity;
import org.smalltech.hashtaglocal_backend.model.IssueActionModel;
import org.smalltech.hashtaglocal_backend.model.IssueCountModel;
import org.smalltech.hashtaglocal_backend.model.IssueStatusModel;
import org.smalltech.hashtaglocal_backend.model.UserProfileModel;
import org.smalltech.hashtaglocal_backend.model.UserSummaryModel;
import org.smalltech.hashtaglocal_backend.repository.IssueActionRepository;
import org.smalltech.hashtaglocal_backend.repository.IssueRepository;
import org.smalltech.hashtaglocal_backend.repository.LocalityRepository;
import org.smalltech.hashtaglocal_backend.repository.UserAuthSessionRepository;
import org.smalltech.hashtaglocal_backend.repository.UserRepository;
import org.springframework.stereotype.Service;

@Service
public class GetProfileService {

  private final UserAuthSessionRepository userAuthSessionRepository;
  private final LocalityRepository localityRepository;
  private final IssueRepository issueRepository;
  private final IssueActionRepository issueActionRepository;
  private static final String DEFAULT_HASHTAG = "#local";

  public GetProfileService(
      UserRepository userRepository,
      UserAuthSessionRepository userAuthSessionRepository,
      LocalityRepository localityRepository,
      IssueRepository issueRepository,
      IssueActionRepository issueActionRepository) {
    this.userAuthSessionRepository = userAuthSessionRepository;
    this.localityRepository = localityRepository;
    this.issueRepository = issueRepository;
    this.issueActionRepository = issueActionRepository;
  }

  /**
   * Get user profile for the authenticated user (me), based on the access token.
   *
   * @param accessToken The bearer token from the Authorization header
   * @param latitude Optional latitude coordinate
   * @param longitude Optional longitude coordinate
   * @return Optional containing the user profile model if found and token is valid
   */
  public Optional<UserProfileModel> getMyProfile(
      String accessToken, Double latitude, Double longitude) {
    // Find the auth session by access token
    Optional<UserAuthSessionEntity> authSession =
        userAuthSessionRepository.findByAccessToken(accessToken);

    if (authSession.isEmpty()) {
      return Optional.empty();
    }

    UserAuthSessionEntity session = authSession.get();

    // Verify session is still active
    if (!session.getIsActive()) {
      return Optional.empty();
    }

    // Check if token has expired
    if (session.getAccessTokenExpiryTs() != null
        && session.getAccessTokenExpiryTs() < System.currentTimeMillis() / 1000) {
      return Optional.empty();
    }

    // Get the user associated with this session
    return Optional.of(mapToProfileModel(session.getUser(), latitude, longitude));
  }

  /** Map UserEntity to UserProfileModel with optional location-based hashtag resolution */
  private UserProfileModel mapToProfileModel(UserEntity user, Double latitude, Double longitude) {
    String hashtag = DEFAULT_HASHTAG;

    // If lat/lng provided, try to resolve locality
    if (latitude != null && longitude != null) {
      var locality =
          localityRepository
              .findContainingLocality(latitude, longitude)
              .or(() -> localityRepository.findNearestLocality(latitude, longitude));

      if (locality.isPresent() && locality.get().getHashtag() != null) {
        hashtag = locality.get().getHashtag();
      }
    }

    UserSummaryModel userSummary = buildUserSummary(user.getId());

    return UserProfileModel.builder()
        .username(user.getUsername())
        .picture(user.getProfilePicture())
        .hashtag(hashtag)
        .userSummary(userSummary)
        .build();
  }

  /** Build user summary with issue counts */
  private UserSummaryModel buildUserSummary(Long userId) {
    long total = issueRepository.countByUserExcludingRejected(userId);
    long onhold = issueRepository.countByUserAndStatus(userId, IssueStatusModel.ONHOLD);
    long open = issueRepository.countByUserAndStatus(userId, IssueStatusModel.OPEN);
    long resolved = issueRepository.countByUserAndStatus(userId, IssueStatusModel.RESOLVED);
    long verify =
        issueActionRepository.countDistinctIssuesByUserAndActionExcludingOwnIssues(
            userId, IssueActionModel.VERIFY);
    long resolvedOthers =
        issueActionRepository.countDistinctIssuesByUserAndActionExcludingOwnIssues(
            userId, IssueActionModel.RESOLVE);

    IssueCountModel issueCount =
        IssueCountModel.builder()
            .total(total)
            .onhold(onhold)
            .open(open)
            .resolved(resolved)
            .verify(verify)
            .resolvedOthers(resolvedOthers)
            .build();

    return UserSummaryModel.builder().issueCount(issueCount).build();
  }
}
