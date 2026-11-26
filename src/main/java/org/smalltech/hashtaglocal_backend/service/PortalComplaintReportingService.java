package org.smalltech.hashtaglocal_backend.service;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.LinkedHashMap;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.locationtech.jts.geom.Point;
import org.smalltech.hashtaglocal_backend.dto.ReportComplaintRequestDTO;
import org.smalltech.hashtaglocal_backend.dto.ReportComplaintResponseDTO;
import org.smalltech.hashtaglocal_backend.entity.GovPortalEntity;
import org.smalltech.hashtaglocal_backend.entity.IssueEntity;
import org.smalltech.hashtaglocal_backend.entity.Locality;
import org.smalltech.hashtaglocal_backend.exception.DownstreamServiceException;
import org.smalltech.hashtaglocal_backend.model.IssueStatusModel;
import org.smalltech.hashtaglocal_backend.model.PortalEnum;
import org.smalltech.hashtaglocal_backend.repository.GovPortalRepository;
import org.smalltech.hashtaglocal_backend.repository.IssueRepository;
import org.smalltech.hashtaglocal_backend.repository.LocalityRepository;
import org.smalltech.hashtaglocal_backend.repository.UserRepository;
import org.smalltech.hashtaglocal_backend.service.scraper0.Scraper0Client;
import org.smalltech.hashtaglocal_backend.service.scraper0.Scraper0Exception;
import org.smalltech.hashtaglocal_backend.service.scraper0.Scraper0Models.WriteResult;
import org.smalltech.hashtaglocal_backend.util.LocationUtil;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Slf4j
public class PortalComplaintReportingService {

  private static final String SUPPORTED_TYPE = "report_issue";
  private static final String BENGALURU_HASHTAG = "#bengaluru";
  private static final String INITIAL_PORTAL_STATUS = IssueStatusModel.OPEN.name();
  private static final String PORTAL_URL =
      "https://www.smartoneblr.com/WssBBMPComplaintRequestDetails.htm";

  /** scraper0 v2 site and action that files a BBMP complaint. */
  static final String SITE_ID = "smartoneblr-com";

  static final String REPORT_ACTION = "report_issue";

  /** {@code timeout_s} of the write; the HTTP read timeout is this plus 30 s. */
  static final int REPORT_TIMEOUT_S = 300;

  static final String MSG_RUNNING =
      "The complaint is still being filed on the portal; try again shortly (retrying this issue"
          + " returns the same job)";
  static final String MSG_OUTCOME_UNKNOWN =
      "The complaint may have been filed; check the portal before retrying";

  private final Scraper0Client scraper0Client;
  private final UserRepository userRepository;
  private final IssueRepository issueRepository;
  private final LocalityRepository localityRepository;
  private final GovPortalRepository govPortalRepository;

  public PortalComplaintReportingService(
      Scraper0Client scraper0Client,
      UserRepository userRepository,
      IssueRepository issueRepository,
      LocalityRepository localityRepository,
      GovPortalRepository govPortalRepository) {
    this.scraper0Client = scraper0Client;
    this.userRepository = userRepository;
    this.issueRepository = issueRepository;
    this.localityRepository = localityRepository;
    this.govPortalRepository = govPortalRepository;
  }

  /** The scraper0 {@code Idempotency-Key} of an issue's complaint: one complaint per issue. */
  static String idempotencyKey(Long issueId) {
    return "hashtaglocal-issue-" + issueId;
  }

  /**
   * Files the complaint through scraper0 ({@code POST /v2/sites/smartoneblr-com/actions/
   * report_issue/write}, {@code Idempotency-Key: hashtaglocal-issue-{issueId}}) and stores the
   * tracking id.
   *
   * <p>200 returns the tracking id. 202 (still running) is a 503 and 409 {@code outcome_unknown} a
   * 409, each with a message for the operator; 422 {@code action_failed} is a 422 with the portal's
   * message; any other scraper0 error is a 502.
   */
  public ReportComplaintResponseDTO reportComplaint(
      String type, Long issueId, Long adminUserId, ReportComplaintRequestDTO request) {
    validateType(type);

    if (issueId == null) {
      throw new IllegalArgumentException("Query parameter issue_id is required");
    }

    if (adminUserId == null) {
      throw new IllegalArgumentException("Authenticated admin user is required");
    }

    if (!scraper0Client.isConfigured()) {
      throw new DownstreamServiceException(
          HttpStatus.INTERNAL_SERVER_ERROR,
          "CONFIGURATION",
          "scraper0.base-url (SCRAPER0_BASE_URL) is not configured");
    }

    validateBengaluruCoordinates(request);

    WriteResult result;
    try {
      result =
          scraper0Client.write(
              SITE_ID,
              REPORT_ACTION,
              reportInput(request.getContext().getAction().getData()),
              auth(request.getContext().getAuth()),
              idempotencyKey(issueId),
              false,
              REPORT_TIMEOUT_S);
    } catch (Scraper0Exception e) {
      throw toDownstream(e);
    }

    if (!result.succeeded()) {
      log.info("Complaint for issue {} still running (job {})", issueId, result.jobId());
      throw new DownstreamServiceException(
          HttpStatus.SERVICE_UNAVAILABLE, "PORTAL_PENDING", MSG_RUNNING);
    }

    Long trackingId = trackingId(result.output());
    if (trackingId == null) {
      throw new DownstreamServiceException(
          HttpStatus.BAD_GATEWAY,
          "DOWNSTREAM_ERROR",
          "Portal complaint API returned invalid response: missing tracking_id");
    }
    if (result.replayed()) {
      log.info("Complaint for issue {} was already filed: tracking id {}", issueId, trackingId);
    }

    persistPortalIssue(issueId, adminUserId, request, trackingId);

    return ReportComplaintResponseDTO.builder().trackingId(trackingId).build();
  }

  private static Map<String, Object> reportInput(ReportComplaintRequestDTO.Data data) {
    Map<String, Object> input = new LinkedHashMap<>();
    input.put("category", data.getCategory());
    input.put("sub_category", data.getSubCategory());
    putIfPresent(input, "description", data.getDescription());
    putIfPresent(input, "media_url", data.getMediaUrl());
    putIfPresent(input, "latitude", data.getLatitude());
    putIfPresent(input, "longitude", data.getLongitude());
    return input;
  }

  private static Map<String, Object> auth(ReportComplaintRequestDTO.Auth auth) {
    Map<String, Object> map = new LinkedHashMap<>();
    map.put("username", auth.getUsername());
    map.put("password", auth.getPassword());
    return map;
  }

  private static void putIfPresent(Map<String, Object> map, String key, String value) {
    if (value != null) {
      map.put(key, value);
    }
  }

  private static Long trackingId(JsonNode output) {
    JsonNode id = output == null ? null : output.get("tracking_id");
    if (id == null || id.isNull()) {
      return null;
    }
    if (id.canConvertToLong()) {
      return id.asLong();
    }
    try {
      return Long.parseLong(id.asText().trim());
    } catch (NumberFormatException e) {
      return null;
    }
  }

  private static DownstreamServiceException toDownstream(Scraper0Exception e) {
    if (Scraper0Exception.OUTCOME_UNKNOWN.equals(e.getCode()) || e.getHttpStatus() == 409) {
      return new DownstreamServiceException(
          HttpStatus.CONFLICT, "OUTCOME_UNKNOWN", MSG_OUTCOME_UNKNOWN);
    }
    if (e.getHttpStatus() == 422) {
      return new DownstreamServiceException(
          HttpStatus.UNPROCESSABLE_ENTITY, "PORTAL_ERROR", e.getMessage());
    }
    if (Scraper0Exception.TIMEOUT.equals(e.getCode())) {
      // The write may have reached the portal; retrying returns the stored outcome.
      return new DownstreamServiceException(
          HttpStatus.GATEWAY_TIMEOUT, "PORTAL_TIMEOUT", MSG_RUNNING);
    }
    return new DownstreamServiceException(HttpStatus.BAD_GATEWAY, "DOWNSTREAM_ERROR", e.describe());
  }

  private void validateType(String type) {
    if (type == null || !SUPPORTED_TYPE.equalsIgnoreCase(type)) {
      throw new IllegalArgumentException(
          "Unsupported portal type '" + type + "'. Supported value: " + SUPPORTED_TYPE);
    }
  }

  @Transactional
  private void persistPortalIssue(
      Long issueId, Long adminUserId, ReportComplaintRequestDTO request, Long trackingId) {
    userRepository
        .findById(adminUserId)
        .orElseThrow(() -> new IllegalArgumentException("Admin user not found: " + adminUserId));

    IssueEntity issue =
        issueRepository
            .findById(issueId)
            .orElseThrow(() -> new IllegalArgumentException("Issue not found: " + issueId));

    // A replayed write (same idempotency key) returns the tracking id stored the first time.
    String tracking = String.valueOf(trackingId);
    boolean alreadyStored =
        govPortalRepository.findByIssueEntityId(issueId).stream()
            .anyMatch(existing -> tracking.equals(existing.getTrackingId()));
    if (alreadyStored) {
      return;
    }

    GovPortalEntity portalIssue =
        GovPortalEntity.builder()
            .issueEntity(issue)
            .trackingId(tracking)
            .portal(resolvePortal(request.getContext().getPortal()))
            .url(resolvePortalUrl())
            .status(INITIAL_PORTAL_STATUS)
            .build();

    govPortalRepository.save(portalIssue);
  }

  private PortalEnum resolvePortal(String portal) {
    if (portal == null || portal.isBlank()) {
      throw new IllegalArgumentException("Unsupported portal: " + portal);
    }

    for (PortalEnum portalEnum : PortalEnum.values()) {
      if (portalEnum.name().equalsIgnoreCase(portal)) {
        return portalEnum;
      }
    }

    throw new IllegalArgumentException("Unsupported portal: " + portal);
  }

  private String resolvePortalUrl() {
    return PORTAL_URL;
  }

  private void validateBengaluruCoordinates(ReportComplaintRequestDTO request) {
    if (request == null
        || request.getContext() == null
        || request.getContext().getAction() == null
        || request.getContext().getAction().getData() == null) {
      throw new IllegalArgumentException(
          "Missing latitude or longitude in request context action data");
    }

    String latitudeRaw = request.getContext().getAction().getData().getLatitude();
    String longitudeRaw = request.getContext().getAction().getData().getLongitude();

    if (latitudeRaw == null || longitudeRaw == null) {
      throw new IllegalArgumentException(
          "Missing latitude or longitude in request context action data");
    }

    final double latitude;
    final double longitude;
    try {
      latitude = Double.parseDouble(latitudeRaw.trim());
      longitude = Double.parseDouble(longitudeRaw.trim());
    } catch (NumberFormatException ex) {
      throw new IllegalArgumentException(
          "Invalid latitude or longitude format in request context action data");
    }

    Point point = LocationUtil.createPoint(latitude, longitude);
    Locality bengaluruLocality = localityRepository.findByHashtag(BENGALURU_HASHTAG).orElse(null);
    if (bengaluruLocality != null
        && bengaluruLocality.getGeoBoundary() != null
        && bengaluruLocality.getGeoBoundary().covers(point)) {
      return;
    }

    Locality locality =
        localityRepository
            .findContainingLocality(latitude, longitude)
            .or(() -> localityRepository.findNearestLocality(latitude, longitude))
            .orElse(null);

    if (locality == null || locality.getName() == null) {
      throw new IllegalArgumentException(
          "Locality is not Bengaluru and given locality is not added to report_issue scraper");
    }

    String normalized = locality.getName().trim().toLowerCase();
    if (!normalized.contains("bengaluru")) {
      throw new IllegalArgumentException(
          "Locality is not Bengaluru and given locality is not added to report_issue scraper");
    }
  }
}
