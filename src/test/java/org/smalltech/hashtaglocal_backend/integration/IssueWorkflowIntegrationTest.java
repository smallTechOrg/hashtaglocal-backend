package org.smalltech.hashtaglocal_backend.integration;

import static org.junit.jupiter.api.Assertions.*;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.InputStream;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.Polygon;
import org.smalltech.hashtaglocal_backend.entity.IssueActionEntity;
import org.smalltech.hashtaglocal_backend.entity.IssueEntity;
import org.smalltech.hashtaglocal_backend.entity.Locality;
import org.smalltech.hashtaglocal_backend.entity.UserAuthProviderEntity;
import org.smalltech.hashtaglocal_backend.entity.UserAuthSessionEntity;
import org.smalltech.hashtaglocal_backend.entity.UserEntity;
import org.smalltech.hashtaglocal_backend.model.IssueActionApprovalStatus;
import org.smalltech.hashtaglocal_backend.model.IssueActionModel;
import org.smalltech.hashtaglocal_backend.model.UserRole;
import org.smalltech.hashtaglocal_backend.repository.IssueActionRepository;
import org.smalltech.hashtaglocal_backend.repository.IssueRepository;
import org.smalltech.hashtaglocal_backend.repository.LocalityRepository;
import org.smalltech.hashtaglocal_backend.repository.LocationRepository;
import org.smalltech.hashtaglocal_backend.repository.MediaRepository;
import org.smalltech.hashtaglocal_backend.repository.UserAuthProviderRepository;
import org.smalltech.hashtaglocal_backend.repository.UserAuthSessionRepository;
import org.smalltech.hashtaglocal_backend.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.reactive.server.WebTestClient;

/**
 * JSON-driven integration test for the Issue workflow.
 *
 * <p>All test scenarios — inputs, expected outputs, and assertions — are defined in {@code
 * src/test/resources/issue-workflow-scenarios.json}. This class is a generic runner that reads each
 * scenario, executes its steps, and asserts expectations.
 *
 * <p>To add a new test scenario, simply add a new entry in the JSON file — no Java code changes
 * required.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@DisplayName("Issue Workflow — JSON-Driven Tests")
class IssueWorkflowIntegrationTest {

  private static final ObjectMapper MAPPER = new ObjectMapper();
  private static JsonNode root;

  @Autowired private WebTestClient webTestClient;
  @Autowired private IssueRepository issueRepository;
  @Autowired private IssueActionRepository issueActionRepository;
  @Autowired private MediaRepository mediaRepository;
  @Autowired private UserRepository userRepository;
  @Autowired private UserAuthSessionRepository userAuthSessionRepository;
  @Autowired private UserAuthProviderRepository userAuthProviderRepository;
  @Autowired private LocalityRepository localityRepository;
  @Autowired private LocationRepository locationRepository;

  // Per-scenario state
  private String userToken;
  private String adminToken;
  private String otherUserToken;
  private Long issueId;
  private Long lastActionId;
  private double lat;
  private double lng;

  // =========================================================================
  // Scenario provider & runner
  // =========================================================================

  static Stream<Arguments> scenarioProvider() throws Exception {
    if (root == null) {
      try (InputStream is =
          IssueWorkflowIntegrationTest.class.getResourceAsStream(
              "/issue-workflow-scenarios.json")) {
        root = MAPPER.readTree(is);
      }
    }
    List<Arguments> args = new ArrayList<>();
    for (JsonNode scenario : root.get("scenarios")) {
      args.add(Arguments.of(scenario.get("name").asText(), scenario.get("description").asText()));
    }
    return args.stream();
  }

  @BeforeEach
  void setUp() {
    // Clean DB in FK-safe order
    issueActionRepository.deleteAll();
    issueRepository.deleteAll();
    mediaRepository.deleteAll();
    userAuthSessionRepository.deleteAll();
    userAuthProviderRepository.deleteAll();
    userRepository.deleteAll();
    locationRepository.deleteAll();
    localityRepository.deleteAll();

    // Read coordinates from JSON config
    lat = root.get("config").get("lat").asDouble();
    lng = root.get("config").get("lng").asDouble();

    // Create test users
    ensureWorldLocality();
    userToken = createUser("reporter", UserRole.USER);
    adminToken = createUser("admin_user", UserRole.ADMIN);
    otherUserToken = createUser("bystander", UserRole.USER);

    // Reset per-scenario state
    issueId = null;
    lastActionId = null;
  }

  @ParameterizedTest(name = "{1}")
  @MethodSource("scenarioProvider")
  void runScenario(String name, String description) {
    JsonNode scenario = findScenario(name);
    int stepNum = 0;
    for (JsonNode step : scenario.get("steps")) {
      stepNum++;
      String desc = step.has("description") ? step.get("description").asText() : "Step " + stepNum;
      try {
        executeStep(step);
      } catch (AssertionError | RuntimeException e) {
        fail("[Step " + stepNum + ": " + desc + "] " + e.getMessage(), e);
      }
    }
  }

  // =========================================================================
  // Step dispatcher
  // =========================================================================

  private void executeStep(JsonNode step) {
    String action = step.get("action").asText();
    switch (action) {
      case "REPORT_ISSUE" -> stepReportIssue(step);
      case "GET_ISSUE" -> stepGetIssue(step);
      case "GET_FEED" -> stepGetFeed(step);
      case "VERIFY_ISSUE" -> stepVerifyIssue(step);
      case "RESOLVE_ISSUE" -> stepResolveIssue(step);
      case "APPROVE_ACTION" -> stepApproveAction(step);
      case "REJECT_ACTION" -> stepRejectAction(step);
      case "RE_APPROVE_LAST_ACTION" -> stepReApproveLast(step);
      case "GET_PENDING" -> stepGetPending(step);
      default -> fail("Unknown action: " + action);
    }
  }

  // =========================================================================
  // Step implementations
  // =========================================================================

  private void stepReportIssue(JsonNode step) {
    String actor = jsonText(step, "actor", "reporter");
    JsonNode expect = step.get("expect");
    int expectedStatus = expect.get("httpStatus").asInt();

    JsonNode input = step.get("input");
    String type = jsonText(input, "type", "POTHOLE");
    String desc = jsonText(input, "description", "Workflow test issue");
    String mediaType = jsonText(input, "mediaType", "PHOTO");
    String mediaUrl =
        jsonText(input, "mediaUrl", "https://example.com/report-" + System.nanoTime() + ".jpg");

    String json = buildReportJson(type, desc, mediaType, mediaUrl);
    WebTestClient.ResponseSpec response = httpPost("/api/v1/issue", actor, json);
    response.expectStatus().isEqualTo(expectedStatus);

    if (expectedStatus == 200) {
      byte[] body = response.expectBody().returnResult().getResponseBody();
      issueId = extractIssueId(body);
      assertNotNull(issueId, "issueId should be extracted from response");
      assertIssueStatus(expect);
    }
  }

  private void stepGetIssue(JsonNode step) {
    String actor = step.get("actor").asText();
    JsonNode expect = step.get("expect");
    int expectedStatus = expect.get("httpStatus").asInt();

    WebTestClient.ResponseSpec response = httpGet("/api/v1/issue/" + issueId, actor);
    response.expectStatus().isEqualTo(expectedStatus);

    if (expectedStatus == 200) {
      assertBodyExpectations(response.expectBody(), expect);
    }
  }

  private void stepGetFeed(JsonNode step) {
    JsonNode expect = step.get("expect");
    int expectedStatus = expect.get("httpStatus").asInt();

    WebTestClient.ResponseSpec response = httpGet("/api/v1/issues", "anonymous");
    response.expectStatus().isEqualTo(expectedStatus);

    if (expectedStatus == 200) {
      assertBodyExpectations(response.expectBody(), expect);
    }
  }

  private void stepIssueAction(JsonNode step, String actionName) {
    String actor = jsonText(step, "actor", "reporter");
    JsonNode expect = step.get("expect");
    int expectedStatus = expect.get("httpStatus").asInt();

    JsonNode input = step.get("input");
    String mediaType = jsonText(input, "mediaType", "PHOTO");
    String mediaUrl =
        jsonText(
            input,
            "mediaUrl",
            "https://example.com/" + actionName.toLowerCase() + "-" + System.nanoTime() + ".jpg");

    String json = buildActionJson(actionName, mediaType, mediaUrl);
    WebTestClient.ResponseSpec response = httpPutWithBody("/api/v1/issue/" + issueId, actor, json);
    response.expectStatus().isEqualTo(expectedStatus);

    if (expectedStatus == 200) {
      assertIssueStatus(expect);
    }
  }

  private void stepVerifyIssue(JsonNode step) {
    stepIssueAction(step, "VERIFY");
  }

  private void stepResolveIssue(JsonNode step) {
    stepIssueAction(step, "RESOLVE");
  }

  private void stepApproveAction(JsonNode step) {
    String actor = jsonText(step, "actor", "admin");
    JsonNode expect = step.get("expect");
    JsonNode input = step.get("input");
    int expectedStatus = expect.get("httpStatus").asInt();

    String pendingType = input.get("pendingActionType").asText();
    IssueActionEntity action = findPendingAction(IssueActionModel.valueOf(pendingType));
    lastActionId = action.getId();

    WebTestClient.ResponseSpec response =
        httpPut("/admin/issue-action/" + action.getId() + "/approve", actor);
    response.expectStatus().isEqualTo(expectedStatus);

    if (expectedStatus == 200) {
      assertIssueStatus(expect);
    }
  }

  private void stepRejectAction(JsonNode step) {
    String actor = jsonText(step, "actor", "admin");
    JsonNode expect = step.get("expect");
    JsonNode input = step.get("input");
    int expectedStatus = expect.get("httpStatus").asInt();

    String pendingType = input.get("pendingActionType").asText();
    IssueActionEntity action = findPendingAction(IssueActionModel.valueOf(pendingType));
    lastActionId = action.getId();

    WebTestClient.ResponseSpec response =
        httpPut("/admin/issue-action/" + action.getId() + "/reject", actor);
    response.expectStatus().isEqualTo(expectedStatus);

    if (expectedStatus == 200) {
      assertIssueStatus(expect);
    }
  }

  private void stepReApproveLast(JsonNode step) {
    JsonNode expect = step.get("expect");
    int expectedStatus = expect.get("httpStatus").asInt();

    assertNotNull(lastActionId, "No previous action to re-approve");

    WebTestClient.ResponseSpec response =
        httpPut("/admin/issue-action/" + lastActionId + "/approve", "admin");
    response.expectStatus().isEqualTo(expectedStatus);
  }

  private void stepGetPending(JsonNode step) {
    JsonNode expect = step.get("expect");
    int expectedStatus = expect.get("httpStatus").asInt();

    WebTestClient.ResponseSpec response = httpGet("/admin/issue-action/pending", "admin");
    response.expectStatus().isEqualTo(expectedStatus);

    if (expectedStatus == 200) {
      assertBodyExpectations(response.expectBody(), expect);
    }
  }

  // =========================================================================
  // Assertion helpers
  // =========================================================================

  private void assertIssueStatus(JsonNode expect) {
    if (expect.has("issueStatus") && issueId != null) {
      IssueEntity issue = issueRepository.findById(issueId).orElseThrow();
      assertEquals(expect.get("issueStatus").asText(), issue.getStatus().name());
    }
  }

  private void assertBodyExpectations(WebTestClient.BodyContentSpec body, JsonNode expect) {
    // Issue-level assertions
    if (expect.has("status")) {
      body.jsonPath("$.data.issue.status").isEqualTo(expect.get("status").asText());
    }
    if (expect.has("mediaCount")) {
      body.jsonPath("$.data.issue.media_urls.length()").isEqualTo(expect.get("mediaCount").asInt());
    }
    if (expect.has("verifyCount")) {
      body.jsonPath("$.data.issue.verify_count").isEqualTo(expect.get("verifyCount").asInt());
    }
    // Feed-level assertions
    if (expect.has("issueCount")) {
      body.jsonPath("$.data.issues.length()").isEqualTo(expect.get("issueCount").asInt());
    }
    if (expect.has("firstIssueStatus")) {
      body.jsonPath("$.data.issues[0].status").isEqualTo(expect.get("firstIssueStatus").asText());
    }
    // Pending queue assertions
    if (expect.has("pendingCount")) {
      body.jsonPath("$.data.length()").isEqualTo(expect.get("pendingCount").asInt());
    }
    if (expect.has("pendingActions")) {
      JsonNode actions = expect.get("pendingActions");
      for (int i = 0; i < actions.size(); i++) {
        JsonNode pa = actions.get(i);
        body.jsonPath("$.data[" + i + "].action").isEqualTo(pa.get("action").asText());
        body.jsonPath("$.data[" + i + "].approval_status")
            .isEqualTo(pa.get("approvalStatus").asText());
      }
    }
  }

  // =========================================================================
  // HTTP helpers
  // =========================================================================

  private WebTestClient.ResponseSpec httpGet(String uri, String actor) {
    String token = resolveToken(actor);
    if (token != null) {
      return webTestClient.get().uri(uri).header("Authorization", "Bearer " + token).exchange();
    }
    return webTestClient.get().uri(uri).exchange();
  }

  private WebTestClient.ResponseSpec httpPost(String uri, String actor, String body) {
    String token = resolveToken(actor);
    if (token != null) {
      return webTestClient
          .post()
          .uri(uri)
          .contentType(MediaType.APPLICATION_JSON)
          .header("Authorization", "Bearer " + token)
          .bodyValue(body)
          .exchange();
    }
    return webTestClient
        .post()
        .uri(uri)
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue(body)
        .exchange();
  }

  private WebTestClient.ResponseSpec httpPut(String uri, String actor) {
    String token = resolveToken(actor);
    if (token != null) {
      return webTestClient.put().uri(uri).header("Authorization", "Bearer " + token).exchange();
    }
    return webTestClient.put().uri(uri).exchange();
  }

  private WebTestClient.ResponseSpec httpPutWithBody(String uri, String actor, String body) {
    String token = resolveToken(actor);
    if (token != null) {
      return webTestClient
          .put()
          .uri(uri)
          .contentType(MediaType.APPLICATION_JSON)
          .header("Authorization", "Bearer " + token)
          .bodyValue(body)
          .exchange();
    }
    return webTestClient
        .put()
        .uri(uri)
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue(body)
        .exchange();
  }

  private String resolveToken(String actor) {
    return switch (actor) {
      case "reporter" -> userToken;
      case "admin" -> adminToken;
      case "other" -> otherUserToken;
      case "anonymous" -> null;
      default -> throw new IllegalArgumentException("Unknown actor: " + actor);
    };
  }

  // =========================================================================
  // JSON builders
  // =========================================================================

  private String buildReportJson(
      String type, String description, String mediaType, String mediaUrl) {
    return """
        {
          "issue": {
            "type": "%s",
            "description": "%s",
            "location": {
              "lat": %s,
              "lng": %s,
              "meta_data": {"city": "Jaipur"}
            },
            "media_urls": [
              {
                "type": "%s",
                "url": "%s",
                "location": {
                  "lat": %s,
                  "lng": %s,
                  "meta_data": {"city": "Jaipur"}
                }
              }
            ]
          }
        }
        """
        .formatted(type, description, lat, lng, mediaType, mediaUrl, lat, lng);
  }

  private String buildActionJson(String action, String mediaType, String mediaUrl) {
    return """
        {
          "issue_action": {
            "action": "%s",
            "media_urls": [
              {
                "type": "%s",
                "url": "%s",
                "location": {
                  "lat": %s,
                  "lng": %s,
                  "meta_data": {"city": "Jaipur"}
                }
              }
            ]
          }
        }
        """
        .formatted(action, mediaType, mediaUrl, lat, lng);
  }

  // =========================================================================
  // Utility helpers
  // =========================================================================

  private void ensureWorldLocality() {
    if (localityRepository.count() == 0) {
      GeometryFactory gf = new GeometryFactory();
      Polygon world =
          gf.createPolygon(
              new Coordinate[] {
                new Coordinate(-180, -90),
                new Coordinate(180, -90),
                new Coordinate(180, 90),
                new Coordinate(-180, 90),
                new Coordinate(-180, -90)
              });
      world.setSRID(4326);
      localityRepository.save(
          Locality.builder().hashtag("world").name("World").geoBoundary(world).build());
    }
  }

  private String createUser(String username, UserRole role) {
    UserEntity user =
        userRepository.save(
            UserEntity.builder()
                .username(username)
                .profilePicture("https://example.com/" + username + ".jpg")
                .locale("en_US")
                .role(role)
                .build());

    UserAuthProviderEntity provider =
        userAuthProviderRepository.save(
            UserAuthProviderEntity.builder()
                .user(user)
                .providerType("TEST")
                .providerUserId(username)
                .email(username + "@test.com")
                .build());

    String token = "token-" + username + "-" + System.nanoTime();
    long expiry = Instant.now().getEpochSecond() + 3600;
    userAuthSessionRepository.saveAndFlush(
        UserAuthSessionEntity.builder()
            .user(user)
            .userAuthProvider(provider)
            .accessToken(token)
            .accessTokenExpiryTs(expiry)
            .refreshToken("refresh-" + token)
            .refreshTokenExpiryTs(expiry + 7200)
            .isActive(true)
            .build());

    return token;
  }

  private IssueActionEntity findPendingAction(IssueActionModel actionType) {
    return issueActionRepository.findAll().stream()
        .filter(a -> a.getAction() == actionType)
        .filter(a -> a.getApprovalStatus() == IssueActionApprovalStatus.PENDING)
        .findFirst()
        .orElseThrow(
            () ->
                new AssertionError("Expected a PENDING " + actionType + " action but none found"));
  }

  private Long extractIssueId(byte[] body) {
    String json = new String(body);
    int idx = json.indexOf("\"issue_id\"");
    if (idx == -1) {
      throw new AssertionError("issue_id not found in response: " + json);
    }
    int colonIdx = json.indexOf(':', idx);
    int start = colonIdx + 1;
    while (start < json.length() && Character.isWhitespace(json.charAt(start))) {
      start++;
    }
    int end = start;
    while (end < json.length() && Character.isDigit(json.charAt(end))) {
      end++;
    }
    return Long.parseLong(json.substring(start, end));
  }

  private JsonNode findScenario(String name) {
    for (JsonNode s : root.get("scenarios")) {
      if (name.equals(s.get("name").asText())) {
        return s;
      }
    }
    throw new IllegalArgumentException("Scenario not found: " + name);
  }

  private String jsonText(JsonNode node, String field, String defaultValue) {
    if (node != null && node.has(field)) {
      return node.get(field).asText();
    }
    return defaultValue;
  }
}
