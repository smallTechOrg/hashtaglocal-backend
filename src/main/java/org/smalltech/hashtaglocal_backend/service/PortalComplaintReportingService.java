package org.smalltech.hashtaglocal_backend.service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.smalltech.hashtaglocal_backend.dto.ReportComplaintRequestDTO;
import org.smalltech.hashtaglocal_backend.dto.ReportComplaintResponseDTO;
import org.smalltech.hashtaglocal_backend.dto.ReportComplaintScrapeResponseDTO;
import org.smalltech.hashtaglocal_backend.entity.GovPortalEntity;
import org.smalltech.hashtaglocal_backend.entity.IssueEntity;
import org.smalltech.hashtaglocal_backend.entity.Location;
import org.smalltech.hashtaglocal_backend.entity.UserEntity;
import org.smalltech.hashtaglocal_backend.exception.DownstreamServiceException;
import org.smalltech.hashtaglocal_backend.model.IssueStatusModel;
import org.smalltech.hashtaglocal_backend.model.IssueTypeModel;
import org.smalltech.hashtaglocal_backend.model.PortalEnum;
import org.smalltech.hashtaglocal_backend.repository.GovPortalRepository;
import org.smalltech.hashtaglocal_backend.repository.IssueRepository;
import org.smalltech.hashtaglocal_backend.repository.UserRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;

@Service
@Slf4j
public class PortalComplaintReportingService {

  private static final String SUPPORTED_TYPE = "report_complaint";
  private static final String INITIAL_PORTAL_STATUS = "REPORTED";

  private final RestTemplate restTemplate;
  private final String scrapeUrl;
  private final LocationService locationService;
  private final UserRepository userRepository;
  private final IssueRepository issueRepository;
  private final GovPortalRepository govPortalRepository;

  public PortalComplaintReportingService(
      RestTemplateBuilder restTemplateBuilder,
      LocationService locationService,
      UserRepository userRepository,
      IssueRepository issueRepository,
      GovPortalRepository govPortalRepository,
      @Value(
              "${portalissue.report.scrape-url:https://staging.api.smalltech.in/webscraperstaging/api/v1/scrape}")
          String scrapeUrl,
      @Value("${portalissue.report.connect-timeout-seconds:15}") long connectTimeoutSeconds,
      @Value("${portalissue.report.read-timeout-seconds:305}") long readTimeoutSeconds) {
    this.locationService = locationService;
    this.userRepository = userRepository;
    this.issueRepository = issueRepository;
    this.govPortalRepository = govPortalRepository;
    this.scrapeUrl = scrapeUrl;
    this.restTemplate =
        restTemplateBuilder
            .requestFactory(
                () -> {
                  SimpleClientHttpRequestFactory requestFactory =
                      new SimpleClientHttpRequestFactory();
                  requestFactory.setConnectTimeout(Duration.ofSeconds(connectTimeoutSeconds));
                  requestFactory.setReadTimeout(Duration.ofSeconds(readTimeoutSeconds));
                  return requestFactory;
                })
            .build();
  }

  @Transactional
  public ReportComplaintResponseDTO reportComplaint(
      String type, Long adminUserId, ReportComplaintRequestDTO request) {
    validateType(type);

    if (adminUserId == null) {
      throw new IllegalArgumentException("Authenticated admin user is required");
    }

    if (scrapeUrl == null || scrapeUrl.isBlank()) {
      throw new DownstreamServiceException(
          HttpStatus.INTERNAL_SERVER_ERROR,
          "CONFIGURATION",
          "portalissue.report.scrape-url is not configured");
    }

    HttpHeaders headers = new HttpHeaders();
    headers.setContentType(MediaType.APPLICATION_JSON);
    HttpEntity<ReportComplaintRequestDTO> entity = new HttpEntity<>(request, headers);

    ReportComplaintScrapeResponseDTO response =
        restTemplate.postForObject(scrapeUrl, entity, ReportComplaintScrapeResponseDTO.class);

    if (response == null || response.getData() == null || response.getData().getTrackingId() == null) {
      throw new DownstreamServiceException(
          HttpStatus.BAD_GATEWAY,
          "DOWNSTREAM_ERROR",
          "Portal complaint API returned invalid response: missing tracking_id");
    }

    persistPortalIssue(adminUserId, request, response.getData().getTrackingId());

    return ReportComplaintResponseDTO.builder().trackingId(response.getData().getTrackingId()).build();
  }

  private void validateType(String type) {
    if (type == null || !SUPPORTED_TYPE.equalsIgnoreCase(type)) {
      throw new IllegalArgumentException(
          "Unsupported portal type '" + type + "'. Supported value: " + SUPPORTED_TYPE);
    }
  }

  private void persistPortalIssue(
      Long adminUserId, ReportComplaintRequestDTO request, Long trackingId) {
    UserEntity adminUser =
        userRepository
            .findById(adminUserId)
            .orElseThrow(
                () -> new IllegalArgumentException("Admin user not found: " + adminUserId));

    ReportComplaintRequestDTO.Data requestData = request.getContext().getAction().getData();
    Location location =
        locationService.createAndSaveLocation(
            parseCoordinate(requestData.getLatitude(), "latitude"),
            parseCoordinate(requestData.getLongitude(), "longitude"),
            buildLocationMetaData(request),
            buildFallbackLocationName(requestData));

    IssueEntity issue =
        IssueEntity.builder()
            .userEntity(adminUser)
            .description(buildIssueDescription(requestData))
            .type(resolveIssueType(requestData))
            .status(IssueStatusModel.OPEN)
            .location(location)
            .createdAt(LocalDateTime.now())
            .updatedAt(LocalDateTime.now())
            .build();

    IssueEntity savedIssue = issueRepository.save(issue);

    GovPortalEntity portalIssue =
        GovPortalEntity.builder()
            .issueEntity(savedIssue)
            .trackingId(String.valueOf(trackingId))
            .portal(resolvePortal(request.getContext().getPortal()))
            .url(scrapeUrl)
            .status(INITIAL_PORTAL_STATUS)
            .metaData(buildPortalMetaData(adminUserId, request, trackingId))
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

  private Double parseCoordinate(String rawValue, String fieldName) {
    if (rawValue == null || rawValue.isBlank()) {
      return null;
    }

    if (!rawValue.matches("[-+]?\\d+(\\.\\d+)?")) {
      throw new IllegalArgumentException(fieldName + " must be a valid decimal string");
    }

    return Double.valueOf(rawValue);
  }

  private IssueTypeModel resolveIssueType(ReportComplaintRequestDTO.Data requestData) {
    String normalized =
        ((requestData.getSubCategory() == null ? "" : requestData.getSubCategory())
                + " "
                + (requestData.getCategory() == null ? "" : requestData.getCategory()))
            .toLowerCase();

    if (normalized.contains("pothole")) {
      return IssueTypeModel.POTHOLE;
    }
    if (normalized.contains("waste") || normalized.contains("garbage")) {
      return IssueTypeModel.WASTE;
    }
    if (normalized.contains("footpath") || normalized.contains("sidewalk")) {
      return IssueTypeModel.FOOTPATH;
    }
    if (normalized.contains("pollution") || normalized.contains("smoke")) {
      return IssueTypeModel.POLLUTION;
    }
    if (normalized.contains("hygiene") || normalized.contains("sanitation")) {
      return IssueTypeModel.HYGIENE;
    }
    if (normalized.contains("safety") || normalized.contains("street light")) {
      return IssueTypeModel.SAFETY;
    }
    return IssueTypeModel.OTHER;
  }

  private String buildIssueDescription(ReportComplaintRequestDTO.Data requestData) {
    if (requestData.getDescription() != null && !requestData.getDescription().isBlank()) {
      return requestData.getDescription();
    }

    return requestData.getCategory() + " / " + requestData.getSubCategory();
  }

  private String buildFallbackLocationName(ReportComplaintRequestDTO.Data requestData) {
    return requestData.getCategory() + " - " + requestData.getSubCategory();
  }

  private Map<String, Object> buildLocationMetaData(ReportComplaintRequestDTO request) {
    ReportComplaintRequestDTO.Data requestData = request.getContext().getAction().getData();

    Map<String, Object> metaData = new LinkedHashMap<>();
    metaData.put("portal", request.getContext().getPortal());
    metaData.put("category", requestData.getCategory());
    metaData.put("sub_category", requestData.getSubCategory());
    metaData.put("media_url", requestData.getMediaUrl());
    return metaData;
  }

  private Map<String, Object> buildPortalMetaData(
      Long adminUserId, ReportComplaintRequestDTO request, Long trackingId) {
    ReportComplaintRequestDTO.Data requestData = request.getContext().getAction().getData();

    Map<String, Object> metaData = new LinkedHashMap<>();
    metaData.put("reported_by_user_id", adminUserId);
    metaData.put("source", request.getSource());
    metaData.put("portal", request.getContext().getPortal());
    metaData.put("action_type", request.getContext().getAction().getType());
    metaData.put("tracking_id", trackingId);
    metaData.put("category", requestData.getCategory());
    metaData.put("sub_category", requestData.getSubCategory());
    metaData.put("description", requestData.getDescription());
    metaData.put("media_url", requestData.getMediaUrl());
    metaData.put("latitude", requestData.getLatitude());
    metaData.put("longitude", requestData.getLongitude());
    return metaData;
  }
}
