package org.smalltech.hashtaglocal_backend.service;

import jakarta.transaction.Transactional;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import org.smalltech.hashtaglocal_backend.entity.Locality;
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
  private final LocationService locationService;
  private final IssueRepository issueRepository;
  private final IssueActionRepository issueActionRepository;
  private final KarmaService karmaService;
  private static final String DEFAULT_HASHTAG = "#local";

  public GetProfileService(
      UserRepository userRepository,
      UserAuthSessionRepository userAuthSessionRepository,
      LocalityRepository localityRepository,
      LocationService locationService,
      IssueRepository issueRepository,
      IssueActionRepository issueActionRepository,
      KarmaService karmaService) {
    this.userAuthSessionRepository = userAuthSessionRepository;
    this.localityRepository = localityRepository;
    this.locationService = locationService;
    this.issueRepository = issueRepository;
    this.issueActionRepository = issueActionRepository;
    this.karmaService = karmaService;
  }

  @Transactional
  public Optional<UserProfileModel> getMyProfile(
      String accessToken, Double latitude, Double longitude) {
    Optional<UserAuthSessionEntity> authSession =
        userAuthSessionRepository.findByAccessToken(accessToken);

    if (authSession.isEmpty()) {
      return Optional.empty();
    }

    UserAuthSessionEntity session = authSession.get();

    if (!session.getIsActive()) {
      return Optional.empty();
    }

    if (session.getAccessTokenExpiryTs() != null
        && session.getAccessTokenExpiryTs() < System.currentTimeMillis() / 1000) {
      return Optional.empty();
    }

    return Optional.of(mapToProfileModel(session.getUser(), latitude, longitude));
  }

  private UserProfileModel mapToProfileModel(UserEntity user, Double latitude, Double longitude) {
    List<String> hashtags;

    if (latitude != null && longitude != null) {
      hashtags = resolveHashtags(latitude, longitude);
      logLocation(user, latitude, longitude);
    } else {
      hashtags = List.of(DEFAULT_HASHTAG);
    }

    karmaService.tryAwardDailyLoginKarma(user);

    UserSummaryModel userSummary = buildUserSummary(user);

    return UserProfileModel.builder()
        .username(user.getUsername())
        .picture(user.getProfilePicture())
        .userRole(user.getRole().name())
        .hashtag(hashtags.get(0))
        .hashtags(hashtags)
        .userSummary(userSummary)
        .build();
  }

  private List<String> resolveHashtags(double latitude, double longitude) {
    List<Locality> containing = localityRepository.findAllContainingLocalities(latitude, longitude);

    if (containing.isEmpty()) {
      var nearest = localityRepository.findNearestLocality(latitude, longitude);
      if (nearest.isPresent()) {
        containing = List.of(nearest.get());
      }
    }

    if (containing.isEmpty()) {
      return List.of(DEFAULT_HASHTAG);
    }

    LinkedHashSet<String> hashtags = new LinkedHashSet<>();
    for (Locality loc : containing) {
      if (loc.getHashtag() != null) hashtags.add(loc.getHashtag());
      Locality parent = loc.getParent();
      if (parent != null && parent.getHashtag() != null) hashtags.add(parent.getHashtag());
    }
    return hashtags.isEmpty() ? List.of(DEFAULT_HASHTAG) : new ArrayList<>(hashtags);
  }

  /** Logs one Location row per app open — raw lat/lng + which user opened the app. */
  private void logLocation(UserEntity user, double latitude, double longitude) {
    var location = locationService.createAndSaveLocation(latitude, longitude, null, null);
    if (location != null) {
      location.setUser(user);
      locationService.save(location);
    }
  }

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
