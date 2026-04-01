package org.smalltech.hashtaglocal_backend.service;

import java.time.Duration;
import lombok.extern.slf4j.Slf4j;
import org.smalltech.hashtaglocal_backend.dto.ReportComplaintRequestDTO;
import org.smalltech.hashtaglocal_backend.dto.ReportComplaintResponseDTO;
import org.smalltech.hashtaglocal_backend.dto.ReportComplaintScrapeResponseDTO;
import org.smalltech.hashtaglocal_backend.exception.DownstreamServiceException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

@Service
@Slf4j
public class PortalComplaintReportingService {

  private final RestTemplate restTemplate;
  private final String scrapeUrl;

  public PortalComplaintReportingService(
      RestTemplateBuilder restTemplateBuilder,
      @Value(
              "${portalissue.report.scrape-url:https://staging.api.smalltech.in/webscraperstaging/api/v1/scrape}")
          String scrapeUrl,
      @Value("${portalissue.report.connect-timeout-seconds:15}") long connectTimeoutSeconds,
      @Value("${portalissue.report.read-timeout-seconds:305}") long readTimeoutSeconds) {
    this.scrapeUrl = scrapeUrl;
    this.restTemplate =
        restTemplateBuilder
            .setConnectTimeout(Duration.ofSeconds(connectTimeoutSeconds))
            .setReadTimeout(Duration.ofSeconds(readTimeoutSeconds))
            .build();
  }

  public ReportComplaintResponseDTO reportComplaint(ReportComplaintRequestDTO request) {
    if (scrapeUrl == null || scrapeUrl.isBlank()) {
      throw new DownstreamServiceException(
          HttpStatus.INTERNAL_SERVER_ERROR,
          "CONFIGURATION",
          "portalissue.report.scrape-url is not configured");
    }

    HttpHeaders headers = new HttpHeaders();
    headers.setContentType(MediaType.APPLICATION_JSON);
    HttpEntity<ReportComplaintRequestDTO> entity = new HttpEntity<>(request, headers);

    try {
      ReportComplaintScrapeResponseDTO response =
          restTemplate.postForObject(scrapeUrl, entity, ReportComplaintScrapeResponseDTO.class);

      if (response == null || response.getData() == null || response.getData().getTrackingId() == null) {
        throw new DownstreamServiceException(
            HttpStatus.BAD_GATEWAY,
            "DOWNSTREAM_ERROR",
            "Portal complaint API returned invalid response: missing tracking_id");
      }

      return ReportComplaintResponseDTO.builder().trackingId(response.getData().getTrackingId()).build();
    } catch (HttpStatusCodeException ex) {
      log.warn("Portal complaint report failed with status={} body={}", ex.getStatusCode(), ex.getResponseBodyAsString());
      throw new DownstreamServiceException(
          HttpStatus.BAD_GATEWAY,
          "DOWNSTREAM_ERROR",
          "Portal complaint API call failed with status " + ex.getStatusCode().value());
    } catch (ResourceAccessException ex) {
      log.warn("Portal complaint report timed out/unreachable: {}", ex.getMessage());
      throw new DownstreamServiceException(
          HttpStatus.GATEWAY_TIMEOUT,
          "DOWNSTREAM_TIMEOUT",
          "Portal complaint API timed out or was unreachable");
    } catch (RestClientException ex) {
      log.warn("Portal complaint report call failed: {}", ex.getMessage());
      throw new DownstreamServiceException(
          HttpStatus.BAD_GATEWAY,
          "DOWNSTREAM_ERROR",
          "Portal complaint API call failed");
    }
  }
}