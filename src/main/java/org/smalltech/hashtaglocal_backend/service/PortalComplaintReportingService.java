package org.smalltech.hashtaglocal_backend.service;

import java.time.Duration;
import lombok.extern.slf4j.Slf4j;
import org.smalltech.hashtaglocal_backend.dto.ReportComplaintRequestDTO;
import org.smalltech.hashtaglocal_backend.dto.ReportComplaintResponseDTO;
import org.smalltech.hashtaglocal_backend.dto.ReportComplaintScrapeResponseDTO;
import org.smalltech.hashtaglocal_backend.entity.GovPortalEntity;
import org.smalltech.hashtaglocal_backend.entity.IssueEntity;
import org.smalltech.hashtaglocal_backend.entity.Locality;
import org.smalltech.hashtaglocal_backend.entity.Location;
import org.smalltech.hashtaglocal_backend.exception.DownstreamServiceException;
import org.smalltech.hashtaglocal_backend.model.IssueStatusModel;
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

  private static final String SUPPORTED_TYPE = "report_issue";
  private static final String INITIAL_PORTAL_STATUS = IssueStatusModel.OPEN.name();
  private static final String PORTAL_URL =
      "https://www.smartoneblr.com/WssBBMPComplaintRequestDetails.htm";

  private final RestTemplate restTemplate;
  private final String scrapeUrl;
  private final UserRepository userRepository;
  private final IssueRepository issueRepository;
  private final GovPortalRepository govPortalRepository;

  public PortalComplaintReportingService(
      RestTemplateBuilder restTemplateBuilder,
      UserRepository userRepository,
      IssueRepository issueRepository,
      GovPortalRepository govPortalRepository,
      @Value(
              "${portalissue.report.scrape-url:https://staging.api.smalltech.in/webscraperstaging/api/v1/scrape}")
          String scrapeUrl,
      @Value("${portalissue.report.connect-timeout-seconds:15}") long connectTimeoutSeconds,
      @Value("${portalissue.report.read-timeout-seconds:305}") long readTimeoutSeconds) {
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
      String type, Long issueId, Long adminUserId, ReportComplaintRequestDTO request) {
    validateType(type);

    if (issueId == null) {
      throw new IllegalArgumentException("Query parameter issue_id is required");
    }

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

    if (response == null
        || response.getData() == null
        || response.getData().getTrackingId() == null) {
      throw new DownstreamServiceException(
          HttpStatus.BAD_GATEWAY,
          "DOWNSTREAM_ERROR",
          "Portal complaint API returned invalid response: missing tracking_id");
    }

    persistPortalIssue(issueId, adminUserId, request, response.getData().getTrackingId());

    return ReportComplaintResponseDTO.builder()
        .trackingId(response.getData().getTrackingId())
        .build();
  }

  private void validateType(String type) {
    if (type == null || !SUPPORTED_TYPE.equalsIgnoreCase(type)) {
      throw new IllegalArgumentException(
          "Unsupported portal type '" + type + "'. Supported value: " + SUPPORTED_TYPE);
    }
  }

  private void persistPortalIssue(
      Long issueId, Long adminUserId, ReportComplaintRequestDTO request, Long trackingId) {
    userRepository
        .findById(adminUserId)
        .orElseThrow(() -> new IllegalArgumentException("Admin user not found: " + adminUserId));

    IssueEntity issue =
        issueRepository
            .findById(issueId)
            .orElseThrow(() -> new IllegalArgumentException("Issue not found: " + issueId));

    GovPortalEntity portalIssue =
        GovPortalEntity.builder()
            .issueEntity(issue)
            .trackingId(String.valueOf(trackingId))
            .portal(resolvePortal(request.getContext().getPortal()))
            .url(resolvePortalUrl(issue))
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

  private String resolvePortalUrl(IssueEntity issue) {
    if (isBengaluruIssue(issue)) {
      return PORTAL_URL;
    }

    throw new IllegalArgumentException(
        "Locality is not Bengaluru and given locality is not added to report_issue scraper");
  }

  private boolean isBengaluruIssue(IssueEntity issue) {
    if (issue == null) {
      return false;
    }

    Location location = issue.getLocation();
    if (location == null) {
      return false;
    }

    Locality locality = location.getLocality();
    if (locality == null) {
      return false;
    }

    String localityName = locality.getName();
    if (localityName == null) {
      return false;
    }

    String normalized = localityName.trim().toLowerCase();
    return normalized.contains("bengaluru");
  }
}
