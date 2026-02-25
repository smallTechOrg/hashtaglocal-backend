package org.smalltech.hashtaglocal_backend.integration;

import static org.junit.jupiter.api.Assertions.*;

import jakarta.persistence.EntityManager;
import java.io.File;
import java.nio.file.Files;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.Polygon;
import org.smalltech.hashtaglocal_backend.entity.IssueEntity;
import org.smalltech.hashtaglocal_backend.entity.Location;
import org.smalltech.hashtaglocal_backend.entity.MediaEntity;
import org.smalltech.hashtaglocal_backend.entity.UserAuthProviderEntity;
import org.smalltech.hashtaglocal_backend.entity.UserAuthSessionEntity;
import org.smalltech.hashtaglocal_backend.entity.UserEntity;
import org.smalltech.hashtaglocal_backend.repository.IssueRepository;
import org.smalltech.hashtaglocal_backend.repository.LocalityRepository;
import org.smalltech.hashtaglocal_backend.repository.LocationRepository;
import org.smalltech.hashtaglocal_backend.repository.MediaRepository;
import org.smalltech.hashtaglocal_backend.repository.UserAuthProviderRepository;
import org.smalltech.hashtaglocal_backend.repository.UserAuthSessionRepository;
import org.smalltech.hashtaglocal_backend.repository.UserRepository;
import org.smalltech.hashtaglocal_backend.util.LocationUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.springframework.util.ResourceUtils;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class IssueReportIntegrationTests {

  @Autowired private WebTestClient webTestClient;

  @Autowired private IssueRepository issueRepository;

  @Autowired private LocationRepository locationRepository;

  @Autowired private MediaRepository mediaRepository;

  @Autowired private LocalityRepository localityRepository;

  @Autowired private UserRepository userRepository;

  @Autowired private UserAuthSessionRepository userAuthSessionRepository;

  @Autowired private UserAuthProviderRepository userAuthProviderRepository;

  @Autowired private EntityManager entityManager;

  private String createAuthenticatedToken() {
    // Create or get the default #world locality if it doesn't exist
    if (localityRepository.findById(1L).isEmpty()) {
      GeometryFactory gf = new GeometryFactory();
      Coordinate[] coords =
          new Coordinate[] {
            new Coordinate(-180, -90),
            new Coordinate(180, -90),
            new Coordinate(180, 90),
            new Coordinate(-180, 90),
            new Coordinate(-180, -90)
          };
      Polygon worldPolygon = gf.createPolygon(coords);
      worldPolygon.setSRID(4326);
      org.smalltech.hashtaglocal_backend.entity.Locality worldLocality =
          org.smalltech.hashtaglocal_backend.entity.Locality.builder()
              .hashtag("#world")
              .name("World")
              .geoBoundary(worldPolygon)
              .build();
      localityRepository.saveAndFlush(worldLocality);
    }

    // Create or get user with unique username per test run
    String username = "test-user-" + System.currentTimeMillis();
    UserEntity user =
        userRepository.save(
            UserEntity.builder()
                .username(username)
                .profilePicture("https://example.com/pic.jpg")
                .locale("en_US")
                .build());

    // Create auth provider
    UserAuthProviderEntity provider =
        userAuthProviderRepository.save(
            UserAuthProviderEntity.builder()
                .user(user)
                .providerType("TEST")
                .providerUserId(username)
                .email("test-" + System.currentTimeMillis() + "@example.com")
                .build());

    // Create auth session with valid token
    String token = "test-token-" + System.currentTimeMillis();
    long expiryTime = System.currentTimeMillis() / 1000 + 3600; // 1 hour from now
    UserAuthSessionEntity session =
        UserAuthSessionEntity.builder()
            .user(user)
            .userAuthProvider(provider)
            .accessToken(token)
            .accessTokenExpiryTs(expiryTime)
            .refreshToken("refresh-" + token)
            .refreshTokenExpiryTs(expiryTime + 7200)
            .isActive(true)
            .build();
    userAuthSessionRepository.saveAndFlush(session);

    return token;
  }

  @Test
  void createIssue_shouldReturnSuccessResponse() throws Exception {
    // Create authenticated token
    String token = createAuthenticatedToken();

    // Load request JSON from test resources
    File jsonFile = ResourceUtils.getFile("classpath:issue-report-request.json");
    String requestJson = Files.readString(jsonFile.toPath());

    webTestClient
        .post()
        .uri("/api/v1/issue")
        .contentType(MediaType.APPLICATION_JSON)
        .header("Authorization", "Bearer " + token)
        .bodyValue(requestJson)
        .exchange()
        .expectStatus()
        .isOk()
        .expectBody()
        .jsonPath("$.data.issue_id")
        .exists();

    // Assert DB entries - find by description since we know it's unique to this
    // test
    List<IssueEntity> issues = issueRepository.findAll();
    IssueEntity issue =
        issues.stream()
            .filter(i -> "Test pothole issue integration".equals(i.getDescription()))
            .findFirst()
            .orElseThrow(
                () ->
                    new AssertionError(
                        "Issue with description 'Test pothole issue integration' not found"));

    assert issue.getType().name().equals("POTHOLE");
    assert issue.getStatus().name().equals("ONHOLD");
    assert issue.getLocation() != null;
    Location issueLocation = locationRepository.findById(issue.getLocation().getId()).orElseThrow();
    assert LocationUtil.getLatitude(issueLocation.getPoint()).equals(28.7041);
    assert LocationUtil.getLongitude(issueLocation.getPoint()).equals(77.1025);
    assert issueLocation.getMetaData() != null
        && issueLocation.getMetaData().toString().contains("Delhi");

    List<MediaEntity> mediaList = mediaRepository.findAll();
    MediaEntity media =
        mediaList.stream()
            .filter(m -> "https://example.com/test-photo.jpg".equals(m.getUrl()))
            .findFirst()
            .orElseThrow(
                () ->
                    new AssertionError(
                        "Media with URL 'https://example.com/test-photo.jpg' not found"));

    assert media.getType().name().equals("PHOTO");
    assert media.getLocation() != null;

    // User_id in media table
    assertNotNull(media.getUser(), "Media user should be assigned");
    assertEquals(
        issue.getUserEntity().getId(),
        media.getUser().getId(),
        "Media user should match issue user");

    Location mediaLocation = locationRepository.findById(media.getLocation().getId()).orElseThrow();
    assert LocationUtil.getLatitude(mediaLocation.getPoint()).equals(28.7041);
    assert LocationUtil.getLongitude(mediaLocation.getPoint()).equals(77.1025);
    assert mediaLocation.getMetaData() != null
        && mediaLocation.getMetaData().toString().contains("Delhi");
  }

  @Test
  void createIssue_shouldAssignBengaluruLocality() {
    // Create authenticated token
    String token = createAuthenticatedToken();

    // Seed Bengaluru locality polygon (SRID 4326, lon/lat order)
    GeometryFactory gf = new GeometryFactory();
    Coordinate[] coords =
        new Coordinate[] {
          new Coordinate(77.3791981, 12.7342888),
          new Coordinate(78.037506, 12.7342888),
          new Coordinate(78.037506, 13.173706),
          new Coordinate(77.3791981, 13.173706),
          new Coordinate(77.3791981, 12.7342888)
        };
    Polygon polygon = gf.createPolygon(coords);
    polygon.setSRID(4326);
    var bengaluru =
        org.smalltech.hashtaglocal_backend.entity.Locality.builder()
            .hashtag("#bengaluru")
            .name("Bengaluru")
            .geoBoundary(polygon)
            .build();
    localityRepository.saveAndFlush(bengaluru);

    String requestJson =
        """
				{
				  "issue": {
				    "type": "POTHOLE",
				    "description": "Pothole in Bengaluru",
				    "location": {
				      "lat": 12.9629,
				      "lng": 77.5775,
				      "meta_data": {"city": "Bengaluru"}
				    },
				    "media_urls": []
				  }
				}
				""";

    webTestClient
        .post()
        .uri("/api/v1/issue")
        .contentType(MediaType.APPLICATION_JSON)
        .header("Authorization", "Bearer " + token)
        .bodyValue(requestJson)
        .exchange()
        .expectStatus()
        .isOk()
        .expectBody()
        .jsonPath("$.data.issue_id")
        .exists();

    List<IssueEntity> issues = issueRepository.findAll();
    IssueEntity latest =
        issues.stream().max((a, b) -> Long.compare(a.getId(), b.getId())).orElseThrow();
    Long locId = latest.getLocation().getId();
    Long localityId =
        ((Number)
                entityManager
                    .createNativeQuery("select locality_id from locations where id = :id")
                    .setParameter("id", locId)
                    .getSingleResult())
            .longValue();
    var locality = localityRepository.findById(localityId).orElse(null);
    assertNotNull(locality, "Locality should be assigned");
    assertEquals("#bengaluru", locality.getHashtag(), "Hashtag assigned: " + locality.getHashtag());
  }
}
