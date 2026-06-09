package org.smalltech.hashtaglocal_backend.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.smalltech.hashtaglocal_backend.entity.Locality;
import org.smalltech.hashtaglocal_backend.entity.UserAuthSessionEntity;
import org.smalltech.hashtaglocal_backend.entity.UserEntity;
import org.smalltech.hashtaglocal_backend.model.IssueActionModel;
import org.smalltech.hashtaglocal_backend.model.IssueStatusModel;
import org.smalltech.hashtaglocal_backend.model.UserProfileModel;
import org.smalltech.hashtaglocal_backend.repository.IssueActionRepository;
import org.smalltech.hashtaglocal_backend.repository.IssueRepository;
import org.smalltech.hashtaglocal_backend.repository.LocalityRepository;
import org.smalltech.hashtaglocal_backend.repository.UserAuthSessionRepository;

/** Unit tests for GetProfileService. */
@DisplayName("GetProfileService Tests")
class GetProfileServiceTest {

  @Mock private UserAuthSessionRepository userAuthSessionRepository;

  @Mock private LocalityRepository localityRepository;

  @Mock private LocationService locationService;

  @Mock private IssueRepository issueRepository;

  @Mock private IssueActionRepository issueActionRepository;

  @Mock private KarmaService karmaService;

  @InjectMocks private GetProfileService profileService;

  @BeforeEach
  void setup() {
    MockitoAnnotations.openMocks(this);
  }

  @Nested
  @DisplayName("Get My Profile")
  class GetMyProfile {

    @Test
    @DisplayName("Should return empty optional when auth session not found")
    void testGetMyProfile_SessionNotFound() {
      // Arrange
      String accessToken = "invalid-token";
      when(userAuthSessionRepository.findByAccessToken(accessToken)).thenReturn(Optional.empty());

      // Act
      Optional<UserProfileModel> result = profileService.getMyProfile(accessToken, null, null);

      // Assert
      assertTrue(result.isEmpty());
      verify(userAuthSessionRepository, times(1)).findByAccessToken(accessToken);
    }

    @Test
    @DisplayName("Should return empty optional when session is inactive")
    void testGetMyProfile_InactiveSession() {
      // Arrange
      String accessToken = "valid-token";
      UserEntity user =
          UserEntity.builder().id(1L).username("testuser").profilePicture("pic.jpg").build();
      UserAuthSessionEntity inactiveSession =
          UserAuthSessionEntity.builder()
              .user(user)
              .accessToken(accessToken)
              .isActive(false)
              .build();

      when(userAuthSessionRepository.findByAccessToken(accessToken))
          .thenReturn(Optional.of(inactiveSession));

      // Act
      Optional<UserProfileModel> result = profileService.getMyProfile(accessToken, null, null);

      // Assert
      assertTrue(result.isEmpty());
      verify(userAuthSessionRepository, times(1)).findByAccessToken(accessToken);
    }

    @Test
    @DisplayName("Should return empty optional when token has expired")
    void testGetMyProfile_TokenExpired() {
      // Arrange
      String accessToken = "valid-token";
      long expiredTime = System.currentTimeMillis() / 1000 - 3600; // 1 hour ago

      UserEntity user =
          UserEntity.builder().id(1L).username("testuser").profilePicture("pic.jpg").build();
      UserAuthSessionEntity expiredSession =
          UserAuthSessionEntity.builder()
              .user(user)
              .accessToken(accessToken)
              .isActive(true)
              .accessTokenExpiryTs(expiredTime)
              .build();

      when(userAuthSessionRepository.findByAccessToken(accessToken))
          .thenReturn(Optional.of(expiredSession));

      // Act
      Optional<UserProfileModel> result = profileService.getMyProfile(accessToken, null, null);

      // Assert
      assertTrue(result.isEmpty());
      verify(userAuthSessionRepository, times(1)).findByAccessToken(accessToken);
    }

    @Test
    @DisplayName("Should return user profile with default hashtag when no lat/lng provided")
    void testGetMyProfile_NoCoordinates_Success() {
      // Arrange
      String accessToken = "valid-token";
      long validExpiryTime = System.currentTimeMillis() / 1000 + 3600; // 1 hour from now

      UserEntity user =
          UserEntity.builder().id(1L).username("testuser").profilePicture("pic.jpg").build();
      UserAuthSessionEntity validSession =
          UserAuthSessionEntity.builder()
              .user(user)
              .accessToken(accessToken)
              .isActive(true)
              .accessTokenExpiryTs(validExpiryTime)
              .build();

      when(userAuthSessionRepository.findByAccessToken(accessToken))
          .thenReturn(Optional.of(validSession));

      // Act
      Optional<UserProfileModel> result = profileService.getMyProfile(accessToken, null, null);

      // Assert
      assertTrue(result.isPresent());
      UserProfileModel profile = result.get();
      assertEquals("testuser", profile.getUsername());
      assertEquals("pic.jpg", profile.getPicture());
      assertEquals("#local", profile.getHashtag());
      assertNotNull(profile.getUserSummary());
      assertNotNull(profile.getUserSummary().getIssueCount());
      assertEquals(0, profile.getUserSummary().getIssueCount().getTotal());

      verify(userAuthSessionRepository, times(1)).findByAccessToken(accessToken);
      verify(localityRepository, never()).findContainingLocality(anyDouble(), anyDouble());
    }

    @Test
    @DisplayName("Should resolve hashtag from containing locality when coordinates provided")
    void testGetMyProfile_WithCoordinates_ContainingLocalityFound() {
      // Arrange
      String accessToken = "valid-token";
      Double latitude = 12.9716;
      Double longitude = 77.5946;
      long validExpiryTime = System.currentTimeMillis() / 1000 + 3600;

      UserEntity user =
          UserEntity.builder().id(1L).username("testuser").profilePicture("pic.jpg").build();
      UserAuthSessionEntity validSession =
          UserAuthSessionEntity.builder()
              .user(user)
              .accessToken(accessToken)
              .isActive(true)
              .accessTokenExpiryTs(validExpiryTime)
              .build();

      Locality locality = Locality.builder().id(1L).hashtag("#bangalore").name("Bangalore").build();

      when(userAuthSessionRepository.findByAccessToken(accessToken))
          .thenReturn(Optional.of(validSession));
      when(localityRepository.findAllContainingLocalities(latitude, longitude))
          .thenReturn(List.of(locality));

      // Act
      Optional<UserProfileModel> result =
          profileService.getMyProfile(accessToken, latitude, longitude);

      // Assert
      assertTrue(result.isPresent());
      UserProfileModel profile = result.get();
      assertEquals("testuser", profile.getUsername());
      assertEquals("pic.jpg", profile.getPicture());
      assertEquals("#bangalore", profile.getHashtag());

      verify(userAuthSessionRepository, times(1)).findByAccessToken(accessToken);
      verify(localityRepository, times(1)).findAllContainingLocalities(latitude, longitude);
      verify(localityRepository, never()).findNearestLocality(anyDouble(), anyDouble());
    }

    @Test
    @DisplayName("Should resolve hashtag from nearest locality when no containing locality found")
    void testGetMyProfile_WithCoordinates_NearestLocalityFound() {
      // Arrange
      String accessToken = "valid-token";
      Double latitude = 13.0827;
      Double longitude = 80.2707;
      long validExpiryTime = System.currentTimeMillis() / 1000 + 3600;

      UserEntity user =
          UserEntity.builder().id(1L).username("testuser").profilePicture("pic.jpg").build();
      UserAuthSessionEntity validSession =
          UserAuthSessionEntity.builder()
              .user(user)
              .accessToken(accessToken)
              .isActive(true)
              .accessTokenExpiryTs(validExpiryTime)
              .build();

      Locality locality = Locality.builder().id(2L).hashtag("#chennai").name("Chennai").build();

      when(userAuthSessionRepository.findByAccessToken(accessToken))
          .thenReturn(Optional.of(validSession));
      when(localityRepository.findAllContainingLocalities(latitude, longitude))
          .thenReturn(List.of());
      when(localityRepository.findNearestLocality(latitude, longitude))
          .thenReturn(Optional.of(locality));

      // Act
      Optional<UserProfileModel> result =
          profileService.getMyProfile(accessToken, latitude, longitude);

      // Assert
      assertTrue(result.isPresent());
      UserProfileModel profile = result.get();
      assertEquals("testuser", profile.getUsername());
      assertEquals("pic.jpg", profile.getPicture());
      assertEquals("#chennai", profile.getHashtag());

      verify(userAuthSessionRepository, times(1)).findByAccessToken(accessToken);
      verify(localityRepository, times(1)).findAllContainingLocalities(latitude, longitude);
      verify(localityRepository, times(1)).findNearestLocality(latitude, longitude);
    }

    @Test
    @DisplayName("Should use default hashtag when no locality found for coordinates")
    void testGetMyProfile_WithCoordinates_NoLocalityFound() {
      // Arrange
      String accessToken = "valid-token";
      Double latitude = 90.0;
      Double longitude = 180.0;
      long validExpiryTime = System.currentTimeMillis() / 1000 + 3600;

      UserEntity user =
          UserEntity.builder().id(1L).username("testuser").profilePicture("pic.jpg").build();
      UserAuthSessionEntity validSession =
          UserAuthSessionEntity.builder()
              .user(user)
              .accessToken(accessToken)
              .isActive(true)
              .accessTokenExpiryTs(validExpiryTime)
              .build();

      when(userAuthSessionRepository.findByAccessToken(accessToken))
          .thenReturn(Optional.of(validSession));
      when(localityRepository.findAllContainingLocalities(latitude, longitude))
          .thenReturn(List.of());
      when(localityRepository.findNearestLocality(latitude, longitude))
          .thenReturn(Optional.empty());

      // Act
      Optional<UserProfileModel> result =
          profileService.getMyProfile(accessToken, latitude, longitude);

      // Assert
      assertTrue(result.isPresent());
      UserProfileModel profile = result.get();
      assertEquals("testuser", profile.getUsername());
      assertEquals("pic.jpg", profile.getPicture());
      assertEquals("#local", profile.getHashtag());

      verify(userAuthSessionRepository, times(1)).findByAccessToken(accessToken);
      verify(localityRepository, times(1)).findAllContainingLocalities(latitude, longitude);
      verify(localityRepository, times(1)).findNearestLocality(latitude, longitude);
    }

    @Test
    @DisplayName("Should use default hashtag when locality hashtag is null")
    void testGetMyProfile_LocalityWithNullHashtag() {
      // Arrange
      String accessToken = "valid-token";
      Double latitude = 12.9716;
      Double longitude = 77.5946;
      long validExpiryTime = System.currentTimeMillis() / 1000 + 3600;

      UserEntity user =
          UserEntity.builder().id(1L).username("testuser").profilePicture("pic.jpg").build();
      UserAuthSessionEntity validSession =
          UserAuthSessionEntity.builder()
              .user(user)
              .accessToken(accessToken)
              .isActive(true)
              .accessTokenExpiryTs(validExpiryTime)
              .build();

      Locality locality = Locality.builder().id(1L).hashtag(null).name("Bangalore").build();

      when(userAuthSessionRepository.findByAccessToken(accessToken))
          .thenReturn(Optional.of(validSession));
      when(localityRepository.findAllContainingLocalities(latitude, longitude))
          .thenReturn(List.of(locality));

      // Act
      Optional<UserProfileModel> result =
          profileService.getMyProfile(accessToken, latitude, longitude);

      // Assert
      assertTrue(result.isPresent());
      UserProfileModel profile = result.get();
      assertEquals("testuser", profile.getUsername());
      assertEquals("pic.jpg", profile.getPicture());
      assertEquals("#local", profile.getHashtag());

      verify(userAuthSessionRepository, times(1)).findByAccessToken(accessToken);
      verify(localityRepository, times(1)).findAllContainingLocalities(latitude, longitude);
    }

    @Test
    @DisplayName("Should handle session with no expiry timestamp")
    void testGetMyProfile_NoExpiryTimestamp() {
      // Arrange
      String accessToken = "valid-token";
      UserEntity user =
          UserEntity.builder().id(1L).username("testuser").profilePicture("pic.jpg").build();
      UserAuthSessionEntity validSession =
          UserAuthSessionEntity.builder()
              .user(user)
              .accessToken(accessToken)
              .isActive(true)
              .accessTokenExpiryTs(null)
              .build();

      when(userAuthSessionRepository.findByAccessToken(accessToken))
          .thenReturn(Optional.of(validSession));

      // Act
      Optional<UserProfileModel> result = profileService.getMyProfile(accessToken, null, null);

      // Assert
      assertTrue(result.isPresent());
      UserProfileModel profile = result.get();
      assertEquals("testuser", profile.getUsername());
      assertEquals("#local", profile.getHashtag());

      verify(userAuthSessionRepository, times(1)).findByAccessToken(accessToken);
    }

    @Test
    @DisplayName("Should use default hashtag when only latitude provided")
    void testGetMyProfile_OnlyLatitude() {
      // Arrange
      String accessToken = "valid-token";
      Double latitude = 12.9716;
      long validExpiryTime = System.currentTimeMillis() / 1000 + 3600;

      UserEntity user =
          UserEntity.builder().id(1L).username("testuser").profilePicture("pic.jpg").build();
      UserAuthSessionEntity validSession =
          UserAuthSessionEntity.builder()
              .user(user)
              .accessToken(accessToken)
              .isActive(true)
              .accessTokenExpiryTs(validExpiryTime)
              .build();

      when(userAuthSessionRepository.findByAccessToken(accessToken))
          .thenReturn(Optional.of(validSession));

      // Act
      Optional<UserProfileModel> result = profileService.getMyProfile(accessToken, latitude, null);

      // Assert
      assertTrue(result.isPresent());
      UserProfileModel profile = result.get();
      assertEquals("#local", profile.getHashtag());

      verify(userAuthSessionRepository, times(1)).findByAccessToken(accessToken);
      verify(localityRepository, never()).findContainingLocality(anyDouble(), anyDouble());
    }

    @Test
    @DisplayName("Should use default hashtag when only longitude provided")
    void testGetMyProfile_OnlyLongitude() {
      // Arrange
      String accessToken = "valid-token";
      Double longitude = 77.5946;
      long validExpiryTime = System.currentTimeMillis() / 1000 + 3600;

      UserEntity user =
          UserEntity.builder().id(1L).username("testuser").profilePicture("pic.jpg").build();
      UserAuthSessionEntity validSession =
          UserAuthSessionEntity.builder()
              .user(user)
              .accessToken(accessToken)
              .isActive(true)
              .accessTokenExpiryTs(validExpiryTime)
              .build();

      when(userAuthSessionRepository.findByAccessToken(accessToken))
          .thenReturn(Optional.of(validSession));

      // Act
      Optional<UserProfileModel> result = profileService.getMyProfile(accessToken, null, longitude);

      // Assert
      assertTrue(result.isPresent());
      UserProfileModel profile = result.get();
      assertEquals("#local", profile.getHashtag());

      verify(userAuthSessionRepository, times(1)).findByAccessToken(accessToken);
      verify(localityRepository, never()).findContainingLocality(anyDouble(), anyDouble());
    }
  }

  @Nested
  @DisplayName("User Summary")
  class UserSummary {

    private UserAuthSessionEntity createValidSession(UserEntity user, String accessToken) {
      long validExpiryTime = System.currentTimeMillis() / 1000 + 3600;
      return UserAuthSessionEntity.builder()
          .user(user)
          .accessToken(accessToken)
          .isActive(true)
          .accessTokenExpiryTs(validExpiryTime)
          .build();
    }

    @Test
    @DisplayName("Should return correct issue counts for user's own issues")
    void testUserSummary_OwnIssueCounts() {
      // Arrange
      String accessToken = "valid-token";
      UserEntity user =
          UserEntity.builder()
              .id(1L)
              .username("testuser")
              .profilePicture("pic.jpg")
              .karmaEarned(25)
              .karmaPending(5)
              .build();
      UserAuthSessionEntity session = createValidSession(user, accessToken);

      when(userAuthSessionRepository.findByAccessToken(accessToken))
          .thenReturn(Optional.of(session));
      when(issueRepository.countByUserExcludingRejected(1L)).thenReturn(12L);
      when(issueRepository.countByUserAndStatus(1L, IssueStatusModel.ONHOLD)).thenReturn(3L);
      when(issueRepository.countByUserAndStatusIn(
              1L, List.of(IssueStatusModel.OPEN, IssueStatusModel.PENDING)))
          .thenReturn(7L);
      when(issueRepository.countByUserAndStatus(1L, IssueStatusModel.RESOLVED)).thenReturn(2L);
      when(issueActionRepository.countDistinctIssuesByUserAndActionExcludingOwnIssues(
              1L, IssueActionModel.VERIFY))
          .thenReturn(5L);
      when(issueActionRepository.countDistinctIssuesByUserAndActionExcludingOwnIssues(
              1L, IssueActionModel.RESOLVE))
          .thenReturn(4L);

      // Act
      Optional<UserProfileModel> result = profileService.getMyProfile(accessToken, null, null);

      // Assert
      assertTrue(result.isPresent());
      var issueCount = result.get().getUserSummary().getIssueCount();
      assertEquals(12, issueCount.getTotal());
      assertEquals(3, issueCount.getOnhold());
      assertEquals(7, issueCount.getOpen());
      assertEquals(2, issueCount.getResolved());
      assertEquals(5, issueCount.getVerify());
      assertEquals(4, issueCount.getResolvedOthers());

      // Karma assertions
      assertEquals(25, result.get().getUserSummary().getKarmaEarned());
      assertEquals(5, result.get().getUserSummary().getKarmaPending());
      verify(karmaService).tryAwardDailyLoginKarma(user);
    }

    @Test
    @DisplayName("Should return zero counts when user has no issues")
    void testUserSummary_NoIssues() {
      // Arrange
      String accessToken = "valid-token";
      UserEntity user =
          UserEntity.builder().id(2L).username("newuser").profilePicture("pic.jpg").build();
      UserAuthSessionEntity session = createValidSession(user, accessToken);

      when(userAuthSessionRepository.findByAccessToken(accessToken))
          .thenReturn(Optional.of(session));
      when(issueRepository.countByUserExcludingRejected(2L)).thenReturn(0L);
      when(issueRepository.countByUserAndStatus(2L, IssueStatusModel.ONHOLD)).thenReturn(0L);
      when(issueRepository.countByUserAndStatusIn(
              2L, List.of(IssueStatusModel.OPEN, IssueStatusModel.PENDING)))
          .thenReturn(0L);
      when(issueRepository.countByUserAndStatus(2L, IssueStatusModel.RESOLVED)).thenReturn(0L);
      when(issueActionRepository.countDistinctIssuesByUserAndActionExcludingOwnIssues(
              2L, IssueActionModel.VERIFY))
          .thenReturn(0L);
      when(issueActionRepository.countDistinctIssuesByUserAndActionExcludingOwnIssues(
              2L, IssueActionModel.RESOLVE))
          .thenReturn(0L);

      // Act
      Optional<UserProfileModel> result = profileService.getMyProfile(accessToken, null, null);

      // Assert
      assertTrue(result.isPresent());
      var issueCount = result.get().getUserSummary().getIssueCount();
      assertEquals(0, issueCount.getTotal());
      assertEquals(0, issueCount.getOnhold());
      assertEquals(0, issueCount.getOpen());
      assertEquals(0, issueCount.getResolved());
      assertEquals(0, issueCount.getVerify());
      assertEquals(0, issueCount.getResolvedOthers());
    }

    @Test
    @DisplayName("Should count verify and resolved_others only for other users' issues")
    void testUserSummary_VerifyAndResolvedOthersExcludeOwnIssues() {
      // Arrange
      String accessToken = "valid-token";
      UserEntity user =
          UserEntity.builder().id(3L).username("active-user").profilePicture("pic.jpg").build();
      UserAuthSessionEntity session = createValidSession(user, accessToken);

      when(userAuthSessionRepository.findByAccessToken(accessToken))
          .thenReturn(Optional.of(session));
      when(issueRepository.countByUserExcludingRejected(3L)).thenReturn(5L);
      when(issueRepository.countByUserAndStatus(3L, IssueStatusModel.ONHOLD)).thenReturn(0L);
      when(issueRepository.countByUserAndStatusIn(
              3L, List.of(IssueStatusModel.OPEN, IssueStatusModel.PENDING)))
          .thenReturn(5L);
      when(issueRepository.countByUserAndStatus(3L, IssueStatusModel.RESOLVED)).thenReturn(0L);
      when(issueActionRepository.countDistinctIssuesByUserAndActionExcludingOwnIssues(
              3L, IssueActionModel.VERIFY))
          .thenReturn(10L);
      when(issueActionRepository.countDistinctIssuesByUserAndActionExcludingOwnIssues(
              3L, IssueActionModel.RESOLVE))
          .thenReturn(3L);

      // Act
      Optional<UserProfileModel> result = profileService.getMyProfile(accessToken, null, null);

      // Assert
      assertTrue(result.isPresent());
      var issueCount = result.get().getUserSummary().getIssueCount();
      assertEquals(5, issueCount.getTotal());
      assertEquals(0, issueCount.getOnhold());
      assertEquals(5, issueCount.getOpen());
      assertEquals(0, issueCount.getResolved());
      assertEquals(10, issueCount.getVerify());
      assertEquals(3, issueCount.getResolvedOthers());

      verify(issueActionRepository)
          .countDistinctIssuesByUserAndActionExcludingOwnIssues(3L, IssueActionModel.VERIFY);
      verify(issueActionRepository)
          .countDistinctIssuesByUserAndActionExcludingOwnIssues(3L, IssueActionModel.RESOLVE);
    }

    @Test
    @DisplayName("Should include user summary alongside profile and hashtag")
    void testUserSummary_IncludedWithProfileData() {
      // Arrange
      String accessToken = "valid-token";
      Double latitude = 12.9716;
      Double longitude = 77.5946;
      UserEntity user =
          UserEntity.builder().id(1L).username("testuser").profilePicture("pic.jpg").build();
      UserAuthSessionEntity session = createValidSession(user, accessToken);

      Locality locality = Locality.builder().id(1L).hashtag("#bangalore").name("Bangalore").build();

      when(userAuthSessionRepository.findByAccessToken(accessToken))
          .thenReturn(Optional.of(session));
      when(localityRepository.findAllContainingLocalities(latitude, longitude))
          .thenReturn(List.of(locality));
      when(issueRepository.countByUserExcludingRejected(1L)).thenReturn(3L);
      when(issueRepository.countByUserAndStatus(1L, IssueStatusModel.ONHOLD)).thenReturn(1L);
      when(issueRepository.countByUserAndStatusIn(
              1L, List.of(IssueStatusModel.OPEN, IssueStatusModel.PENDING)))
          .thenReturn(2L);
      when(issueRepository.countByUserAndStatus(1L, IssueStatusModel.RESOLVED)).thenReturn(0L);
      when(issueActionRepository.countDistinctIssuesByUserAndActionExcludingOwnIssues(
              1L, IssueActionModel.VERIFY))
          .thenReturn(0L);
      when(issueActionRepository.countDistinctIssuesByUserAndActionExcludingOwnIssues(
              1L, IssueActionModel.RESOLVE))
          .thenReturn(0L);

      // Act
      Optional<UserProfileModel> result =
          profileService.getMyProfile(accessToken, latitude, longitude);

      // Assert
      assertTrue(result.isPresent());
      UserProfileModel profile = result.get();
      assertEquals("testuser", profile.getUsername());
      assertEquals("pic.jpg", profile.getPicture());
      assertEquals("#bangalore", profile.getHashtag());
      assertNotNull(profile.getUserSummary());
      assertEquals(3, profile.getUserSummary().getIssueCount().getTotal());
      assertEquals(1, profile.getUserSummary().getIssueCount().getOnhold());
      assertEquals(2, profile.getUserSummary().getIssueCount().getOpen());
    }
  }
}
