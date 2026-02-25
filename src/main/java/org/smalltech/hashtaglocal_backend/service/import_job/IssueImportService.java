package org.smalltech.hashtaglocal_backend.service.import_job;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.Polygon;
import org.locationtech.jts.geom.PrecisionModel;
import org.smalltech.hashtaglocal_backend.dto.BlrPagesIssueDTO;
import org.smalltech.hashtaglocal_backend.dto.BlrPagesResponse;
import org.smalltech.hashtaglocal_backend.entity.IssueActionEntity;
import org.smalltech.hashtaglocal_backend.entity.IssueEntity;
import org.smalltech.hashtaglocal_backend.entity.IssueImportJob;
import org.smalltech.hashtaglocal_backend.entity.IssueImportStatus;
import org.smalltech.hashtaglocal_backend.entity.Locality;
import org.smalltech.hashtaglocal_backend.entity.Location;
import org.smalltech.hashtaglocal_backend.entity.MediaEntity;
import org.smalltech.hashtaglocal_backend.entity.UserEntity;
import org.smalltech.hashtaglocal_backend.model.IssueActionApprovalStatus;
import org.smalltech.hashtaglocal_backend.model.IssueActionModel;
import org.smalltech.hashtaglocal_backend.model.IssueImportSource;
import org.smalltech.hashtaglocal_backend.model.IssueStatusModel;
import org.smalltech.hashtaglocal_backend.model.IssueTypeModel;
import org.smalltech.hashtaglocal_backend.model.MediaTypeModel;
import org.smalltech.hashtaglocal_backend.repository.IssueActionRepository;
import org.smalltech.hashtaglocal_backend.repository.IssueImportJobRepository;
import org.smalltech.hashtaglocal_backend.repository.IssueImportStatusRepository;
import org.smalltech.hashtaglocal_backend.repository.IssueRepository;
import org.smalltech.hashtaglocal_backend.repository.LocalityRepository;
import org.smalltech.hashtaglocal_backend.repository.LocationRepository;
import org.smalltech.hashtaglocal_backend.repository.MediaRepository;
import org.smalltech.hashtaglocal_backend.repository.UserRepository;
import org.smalltech.hashtaglocal_backend.service.GCSService;
import org.smalltech.hashtaglocal_backend.util.LocationUtil;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

@Service
@RequiredArgsConstructor
@Slf4j
public class IssueImportService {

  private static final String BLR_PAGES_URL = "https://blr-map-api.warlockdn.workers.dev/api/data";
  private static final String DESCRIPTION_TEMPLATE = "Sourced from blr-pages, category: %s";
  private static final String BENGALURU_HASHTAG = "#bengaluru";
  private static final String WORLD_HASHTAG = "world";
  private static final GeometryFactory GEOMETRY_FACTORY =
      new GeometryFactory(new PrecisionModel(), 4326);

  private final IssueImportJobRepository importJobRepository;
  private final IssueImportStatusRepository importStatusRepository;
  private final IssueRepository issueRepository;
  private final IssueActionRepository issueActionRepository;
  private final MediaRepository mediaRepository;
  private final LocationRepository locationRepository;
  private final LocalityRepository localityRepository;
  private final UserRepository userRepository;
  private final RestTemplate restTemplate;
  private final GCSService gcsService;
  private final ObjectMapper objectMapper = new ObjectMapper();

  public IssueImportJob importBlrPages() {
    IssueImportJob job =
        IssueImportJob.builder()
            .source(IssueImportSource.BLR_PAGES)
            .status(IssueImportJob.JobStatus.RUNNING)
            .startedAt(LocalDateTime.now())
            .build();

    job = importJobRepository.save(job);

    try {
      log.info("Fetching issues from blr-pages API: {}", BLR_PAGES_URL);
      BlrPagesResponse response = restTemplate.getForObject(BLR_PAGES_URL, BlrPagesResponse.class);
      log.info("API response success: {}", response != null && response.isSuccess());
      List<BlrPagesIssueDTO> issues =
          response == null || response.getResult() == null || response.getResult().getData() == null
              ? List.of()
              : response.getResult().getData();
      log.info("Found {} issues to import", issues.size());
      job.setTotalIssues(issues.size());

      for (BlrPagesIssueDTO dto : issues) {
        IssueImportStatus status = processBlrPagesIssue(dto, job);
        switch (status.getImportStatus()) {
          case SUCCESS -> job.setSuccessCount(job.getSuccessCount() + 1);
          case SKIPPED -> job.setSkippedCount(job.getSkippedCount() + 1);
          case FAILED -> job.setFailureCount(job.getFailureCount() + 1);
          default -> {}
        }
        importJobRepository.save(job);
      }

      job.setStatus(IssueImportJob.JobStatus.COMPLETED);
      job.setCompletedAt(LocalDateTime.now());
    } catch (Exception e) {
      log.error("Issue import failed", e);
      job.setStatus(IssueImportJob.JobStatus.FAILED);
      job.setErrorMessage(e.getMessage());
      job.setCompletedAt(LocalDateTime.now());
    }

    return importJobRepository.save(job);
  }

  private IssueImportStatus processBlrPagesIssue(BlrPagesIssueDTO dto, IssueImportJob job) {
    String sourceIssueId = resolveSourceIssueId(dto);
    IssueImportStatus status =
        IssueImportStatus.builder()
            .job(job)
            .source(IssueImportSource.BLR_PAGES)
            .sourceIssueId(sourceIssueId)
            .sourceCreatedAt(parseCreatedAt(dto.getCreatedAt()))
            .sourcePayload(serialize(dto))
            .build();

    if (importStatusRepository.existsBySourceAndSourceIssueId(
        IssueImportSource.BLR_PAGES, sourceIssueId)) {
      status.setImportStatus(IssueImportStatus.ImportStatus.SKIPPED);
      status.setErrorMessage("Duplicate source id for blr-pages");
      status.setUpdatedAt(LocalDateTime.now());
      return importStatusRepository.save(status);
    }

    try {
      Locality locality = resolveLocality();
      Location location = persistLocation(dto, locality);
      UserEntity user = resolveUser();

      LocalDateTime createdAt =
          status.getSourceCreatedAt() != null
              ? status.getSourceCreatedAt()
              : LocalDateTime.now(ZoneOffset.UTC);

      IssueEntity issue =
          IssueEntity.builder()
              .type(IssueTypeModel.POTHOLE)
              .status(IssueStatusModel.OPEN)
              .description(
                  String.format(
                      Locale.US,
                      DESCRIPTION_TEMPLATE,
                      dto.getCategory() == null ? "unknown" : dto.getCategory().toString()))
              .createdAt(createdAt)
              .updatedAt(createdAt)
              .location(location)
              .userEntity(user)
              .build();

      issue = issueRepository.save(issue);

      DownloadedImage downloaded = downloadImage(dto.getImage());
      String objectName = "issues/blr-pages/" + sanitizeForPath(sourceIssueId) + ".jpg";
      String gcsPath =
          gcsService.uploadObject(objectName, downloaded.data(), downloaded.contentType());

      MediaEntity media =
          MediaEntity.builder()
              .type(MediaTypeModel.PHOTO)
              .url(gcsPath)
              .location(location)
              .createdAt(issue.getCreatedAt())
              .build();
      media = mediaRepository.save(media);

      // Create a REPORT action to own the media (imported issues are already OPEN,
      // so approval is NOT_REQUIRED)
      IssueActionEntity reportAction =
          IssueActionEntity.builder()
              .issueEntity(issue)
              .userEntity(user)
              .action(IssueActionModel.REPORT)
              .approvalStatus(IssueActionApprovalStatus.NOT_REQUIRED)
              .media(media)
              .createdAt(issue.getCreatedAt())
              .build();
      issueActionRepository.save(reportAction);

      status.setIssue(issue);
      status.setImportStatus(IssueImportStatus.ImportStatus.SUCCESS);
      status.setStoredMediaPath(gcsPath);
    } catch (Exception e) {
      log.error("Failed to import blr-pages issue {}", sourceIssueId, e);
      status.setImportStatus(IssueImportStatus.ImportStatus.FAILED);
      status.setErrorMessage(e.getMessage());
    }

    status.setUpdatedAt(LocalDateTime.now());
    return importStatusRepository.save(status);
  }

  private String resolveSourceIssueId(BlrPagesIssueDTO dto) {
    if (dto.getUuid() != null && !dto.getUuid().isBlank()) {
      return dto.getUuid();
    }
    return "blr-pages-" + UUID.randomUUID();
  }

  private String serialize(BlrPagesIssueDTO dto) {
    try {
      return objectMapper.writeValueAsString(dto);
    } catch (JsonProcessingException e) {
      return null;
    }
  }

  private LocalDateTime parseCreatedAt(String createdAt) {
    if (createdAt == null || createdAt.isBlank()) {
      return null;
    }
    try {
      Instant instant = Instant.parse(createdAt);
      return LocalDateTime.ofInstant(instant, ZoneOffset.UTC);
    } catch (Exception ex) {
      log.warn("Unable to parse created_at {}, defaulting to now", createdAt);
      return LocalDateTime.now(ZoneOffset.UTC);
    }
  }

  private Locality resolveLocality() {
    return localityRepository
        .findByHashtag(BENGALURU_HASHTAG)
        .orElseGet(
            () ->
                localityRepository
                    .findByHashtag(WORLD_HASHTAG)
                    .orElseGet(this::createBengaluruLocalityPlaceholder));
  }

  private Locality createBengaluruLocalityPlaceholder() {
    Polygon boundary =
        GEOMETRY_FACTORY.createPolygon(
            new Coordinate[] {
              new Coordinate(77.4, 12.7),
              new Coordinate(77.8, 12.7),
              new Coordinate(77.8, 13.1),
              new Coordinate(77.4, 13.1),
              new Coordinate(77.4, 12.7)
            });
    boundary.setSRID(4326);
    return localityRepository.save(
        Locality.builder()
            .hashtag(BENGALURU_HASHTAG)
            .name("Bengaluru")
            .geoBoundary(boundary)
            .build());
  }

  private Location persistLocation(BlrPagesIssueDTO dto, Locality locality) {
    if (dto.getLat() == null || dto.getLng() == null) {
      throw new IllegalArgumentException("lat/long missing in source payload");
    }

    return locationRepository.save(
        Location.builder()
            .point(LocationUtil.createPoint(dto.getLat(), dto.getLng()))
            .locality(locality)
            .name("Bengaluru")
            .metaData(
                Map.of(
                    "source",
                    "blr-pages",
                    "category",
                    dto.getCategory() == null ? "unknown" : dto.getCategory()))
            .build());
  }

  private UserEntity resolveUser() {
    return userRepository
        .findById(1L)
        .orElseGet(
            () ->
                userRepository.findAll().stream()
                    .findFirst()
                    .orElseGet(
                        () ->
                            userRepository.save(
                                UserEntity.builder()
                                    .username("blr-pages")
                                    .locale("en")
                                    .profilePicture("https://example.com/blr-pages.png")
                                    .build())));
  }

  private DownloadedImage downloadImage(String url) {
    if (url == null || url.isBlank()) {
      throw new IllegalArgumentException("Image url missing");
    }

    ResponseEntity<byte[]> response =
        restTemplate.exchange(
            url, HttpMethod.GET, new HttpEntity<>(new HttpHeaders()), byte[].class);

    if (!response.getStatusCode().is2xxSuccessful() || response.getBody() == null) {
      throw new IllegalStateException("Failed to download image from " + url);
    }

    String contentType =
        response.getHeaders().getContentType() != null
            ? response.getHeaders().getContentType().toString()
            : "application/octet-stream";

    return new DownloadedImage(response.getBody(), contentType);
  }

  private String sanitizeForPath(String input) {
    String sanitized = input.replaceAll("[^A-Za-z0-9-]", "-");
    if (sanitized.isEmpty()) {
      sanitized = UUID.randomUUID().toString();
    }
    if (sanitized.length() > 100) {
      sanitized = sanitized.substring(0, 100);
    }
    return sanitized;
  }

  private record DownloadedImage(byte[] data, String contentType) {}
}
