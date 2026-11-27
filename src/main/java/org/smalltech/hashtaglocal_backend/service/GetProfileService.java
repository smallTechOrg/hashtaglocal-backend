package org.smalltech.hashtaglocal_backend.service;

import jakarta.transaction.Transactional;
import java.util.List;
import java.util.Optional;
import org.smalltech.hashtaglocal_backend.entity.Location;
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

  private final UserRepository userRepository;
  private final UserAuthSessionRepository userAuthSessionRepository;
  private final LocalityRepository localityRepository;
  private final LocationService locationService;
  private final IssueRepository issueRepository;
  private final IssueActionRepository issueActionRepository;
  private final KarmaService karmaService;

  public GetProfileService(
      UserRepository userRepository,
      UserAuthSessionRepository userAuthSessionRepository,
      LocalityRepository localityRepository,
      LocationService locationService,
      IssueRepository issueRepository,
      IssueActionRepository issueActionRepository,
      KarmaService karmaService) {
    this.userRepository = userRepository;
    this.userAuthSessionRepository = userAuthSessionRepository;
    this.localityRepository = localityRepository;
    this.locationService = locationService;
    this.issueRepository = issueRepository;
    this.issueActionRepository = issueActionRepository;
    this.karmaService = karmaService;
  }

  /**
   * Get user profile for the authenticated user (me), based on the access token.
   *
   * @param accessToken The bearer token from the Authorization header
   * @param latitude Optional latitude coordinate
   * @param longitude Optional longitude coordinate
   * @return Optional containing the user profile model if found and token is valid
   */
  @Transactional
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

  /**
   * Map UserEntity to UserProfileModel with location-based hashtag resolution.
   *
   * <p>The hashtag is the locality at the given lat/lng; without coordinates (e.g. the app's first
   * launch, before GPS is ready) it is the locality of the user's last saved location. When neither
   * is known it is null. We used to send a placeholder "#local" here, but clients treated it as a
   * real hashtag and asked for its feed, which is a 404.
   */
  private UserProfileModel mapToProfileModel(UserEntity user, Double latitude, Double longitude) {
    String hashtag = null;

    // If lat/lng provided, resolve locality for hashtag and persist user's location
    if (latitude != null && longitude != null) {
      var locality =
          localityRepository
              .findContainingLocality(latitude, longitude)
              .or(() -> localityRepository.findNearestLocality(latitude, longitude));

      if (locality.isPresent() && locality.get().getHashtag() != null) {
        hashtag = locality.get().getHashtag();
      }

      Location updatedLocation =
          locationService.upsertUserLocation(user.getLocation(), latitude, longitude);
      user.setLocation(updatedLocation);
      userRepository.save(user);
    }

    if (hashtag == null) {
      hashtag = savedLocalityHashtag(user);
    }

    // Award daily login karma (idempotent)
    karmaService.tryAwardDailyLoginKarma(user);

    UserSummaryModel userSummary = buildUserSummary(user);

    return UserProfileModel.builder()
        .username(user.getUsername())
        .picture(user.getProfilePicture())
        .userRole(user.getRole().name())
        .hashtag(hashtag)
        .userSummary(userSummary)
        .build();
  }

  /** Hashtag of the locality the user's saved location belongs to, or null if there is none. */
  private static String savedLocalityHashtag(UserEntity user) {
    Location location = user.getLocation();
    if (location == null || location.getLocality() == null) {
      return null;
    }
    return location.getLocality().getHashtag();
  }

  /** Build user summary with issue counts and karma */
  public UserSummaryModel buildUserSummary(UserEntity user) {
    Long userId = user.getId();
    long total = issueRepository.countByUserExcludingRejected(userId);
    long onhold = issueRepository.countByUserAndStatus(userId, IssueStatusModel.ONHOLD);
    long open =
        issueRepository.countByUserAndStatusIn(
            userId, List.of(IssueStatusModel.OPEN, IssueStatusModel.PENDING));
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

    return UserSummaryModel.builder()
        .issueCount(issueCount)
        .karmaEarned(user.getKarmaEarned())
        .karmaPending(user.getKarmaPending())
        .build();
  }
}
