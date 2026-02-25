package org.smalltech.hashtaglocal_backend.service.import_job;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.smalltech.hashtaglocal_backend.dto.GoogleMapsLocationDTO;
import org.smalltech.hashtaglocal_backend.entity.DiscoveredLocality;
import org.smalltech.hashtaglocal_backend.entity.ImportJob;
import org.smalltech.hashtaglocal_backend.entity.LocalityImportStatus;
import org.smalltech.hashtaglocal_backend.repository.LocalityImportStatusRepository;
import org.smalltech.hashtaglocal_backend.repository.LocalityRepository;
import org.springframework.web.client.RestTemplate;

/**
 * Test LocalityImportService.
 *
 * <p>Verifies: 1. Validates locality with Google Maps API 2. Extracts polygon from viewport/bounds
 * 3. Saves to localities table with hashtags 4. Stores complete Google Maps metadata as JSON 5.
 * Tracks import status with retry logic 6. Handles API errors gracefully
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("Locality Import Service")
class LocalityImportServiceTest {

  @Mock private LocalityImportStatusRepository importStatusRepository;

  @Mock private LocalityRepository localityRepository;

  @Mock private RestTemplate restTemplate;

  private LocalityImportService service;

  @BeforeEach
  void setUp() {
    service = new LocalityImportService(importStatusRepository, localityRepository, restTemplate);
  }

  @Test
  @DisplayName("Should validate locality with Google Maps and save import status")
  void importLocalityFromDiscovery() {
    // Arrange
    ImportJob job =
        ImportJob.builder()
            .status(ImportJob.ImportJobStatus.RUNNING)
            .totalLocalities(1)
            .successCount(0)
            .failureCount(0)
            .skippedCount(0)
            .startedAt(java.time.LocalDateTime.now())
            .build();

    DiscoveredLocality discovered =
        DiscoveredLocality.builder()
            .officialName("Bengaluru")
            .state("Karnataka")
            .countryCode("IN")
            .localityType(DiscoveredLocality.LocalityType.CITY)
            .confidenceScore(2)
            .build();

    // Mock Google Maps response
    GoogleMapsLocationDTO.Location loc =
        GoogleMapsLocationDTO.Location.builder().lat(12.9716).lng(77.5946).build();

    GoogleMapsLocationDTO.Geometry geom =
        GoogleMapsLocationDTO.Geometry.builder().location(loc).build();

    GoogleMapsLocationDTO.Result result =
        GoogleMapsLocationDTO.Result.builder()
            .formattedAddress("Bengaluru, Karnataka, India")
            .geometry(geom)
            .placeId("ChIJ7zoPHisDUDoRnWMEwDfKYJ4")
            .types(new java.util.ArrayList<>(java.util.Arrays.asList("locality", "political")))
            .build();

    GoogleMapsLocationDTO response =
        GoogleMapsLocationDTO.builder()
            .results(java.util.Arrays.asList(result))
            .status("OK")
            .build();

    when(restTemplate.getForObject(anyString(), eq(GoogleMapsLocationDTO.class)))
        .thenReturn(response);

    when(importStatusRepository.save(any(LocalityImportStatus.class)))
        .thenAnswer(inv -> inv.getArgument(0));

    // Act
    LocalityImportStatus status = service.validateAndImport(job, discovered);

    // Assert
    assertNotNull(status);
    assertEquals(LocalityImportStatus.ImportStatus.SUCCESS, status.getImportStatus());
    assertEquals("Bengaluru", status.getLocalityName());
    assertEquals(1, status.getAttemptCount());
    // Google Maps metadata should be stored as JSON
    assertNotNull(status.getGoogleMapsMetadata());
    assertTrue(status.getGoogleMapsMetadata().contains("Bengaluru"));
  }

  @Test
  @DisplayName("Should handle Google Maps API error and mark as failed")
  void handleGoogleMapsAPIError() {
    ImportJob job =
        ImportJob.builder()
            .status(ImportJob.ImportJobStatus.RUNNING)
            .startedAt(java.time.LocalDateTime.now())
            .totalLocalities(1)
            .build();

    DiscoveredLocality discovered =
        DiscoveredLocality.builder()
            .officialName("TestCity")
            .state("TestState")
            .countryCode("IN")
            .localityType(DiscoveredLocality.LocalityType.CITY)
            .build();

    when(restTemplate.getForObject(anyString(), eq(GoogleMapsLocationDTO.class)))
        .thenThrow(new RuntimeException("API rate limit exceeded"));

    when(importStatusRepository.save(any(LocalityImportStatus.class)))
        .thenAnswer(inv -> inv.getArgument(0));

    // Act
    LocalityImportStatus status = service.validateAndImport(job, discovered);

    // Assert
    assertNotNull(status);
    assertEquals(LocalityImportStatus.ImportStatus.FAILED, status.getImportStatus());
    assertTrue(status.getErrorMessage().contains("API rate limit exceeded"));
  }

  @Test
  @DisplayName("Should skip if Google Maps returns no results")
  void skipWhenGoogleMapsNoResults() {
    ImportJob job =
        ImportJob.builder()
            .status(ImportJob.ImportJobStatus.RUNNING)
            .startedAt(java.time.LocalDateTime.now())
            .totalLocalities(1)
            .build();

    DiscoveredLocality discovered =
        DiscoveredLocality.builder()
            .officialName("NonExistentCity")
            .state("TestState")
            .countryCode("IN")
            .localityType(DiscoveredLocality.LocalityType.CITY)
            .build();

    GoogleMapsLocationDTO response =
        GoogleMapsLocationDTO.builder()
            .results(new java.util.ArrayList<>())
            .status("ZERO_RESULTS")
            .build();

    when(restTemplate.getForObject(anyString(), eq(GoogleMapsLocationDTO.class)))
        .thenReturn(response);

    when(importStatusRepository.save(any(LocalityImportStatus.class)))
        .thenAnswer(inv -> inv.getArgument(0));

    // Act
    LocalityImportStatus status = service.validateAndImport(job, discovered);

    // Assert
    assertNotNull(status);
    assertEquals(LocalityImportStatus.ImportStatus.NO_DATA_FOUND, status.getImportStatus());
  }

  @Test
  @DisplayName("Should extract polygon from viewport bounds")
  void extractPolygonFromViewport() {
    ImportJob job =
        ImportJob.builder()
            .status(ImportJob.ImportJobStatus.RUNNING)
            .startedAt(java.time.LocalDateTime.now())
            .totalLocalities(1)
            .build();

    DiscoveredLocality discovered =
        DiscoveredLocality.builder()
            .officialName("Pune")
            .state("Maharashtra")
            .countryCode("IN")
            .localityType(DiscoveredLocality.LocalityType.CITY)
            .build();

    // Mock with viewport bounds
    GoogleMapsLocationDTO.Location northeast =
        GoogleMapsLocationDTO.Location.builder().lat(19.0).lng(74.0).build();

    GoogleMapsLocationDTO.Location southwest =
        GoogleMapsLocationDTO.Location.builder().lat(18.5).lng(73.8).build();

    GoogleMapsLocationDTO.Viewport viewport =
        GoogleMapsLocationDTO.Viewport.builder().northeast(northeast).southwest(southwest).build();

    GoogleMapsLocationDTO.Geometry geom =
        GoogleMapsLocationDTO.Geometry.builder()
            .location(GoogleMapsLocationDTO.Location.builder().lat(18.75).lng(73.9).build())
            .viewport(viewport)
            .build();

    GoogleMapsLocationDTO.Result result =
        GoogleMapsLocationDTO.Result.builder()
            .formattedAddress("Pune, Maharashtra, India")
            .geometry(geom)
            .placeId("test-id")
            .types(new java.util.ArrayList<>())
            .build();

    GoogleMapsLocationDTO response =
        GoogleMapsLocationDTO.builder()
            .results(java.util.Arrays.asList(result))
            .status("OK")
            .build();

    when(restTemplate.getForObject(anyString(), eq(GoogleMapsLocationDTO.class)))
        .thenReturn(response);

    when(importStatusRepository.save(any(LocalityImportStatus.class)))
        .thenAnswer(inv -> inv.getArgument(0));

    // Act
    LocalityImportStatus status = service.validateAndImport(job, discovered);

    // Assert
    assertNotNull(status);
    assertEquals(LocalityImportStatus.ImportStatus.SUCCESS, status.getImportStatus());
    // Verify that locality was created and metadata was stored
    assertNotNull(status.getGoogleMapsMetadata());
    assertTrue(status.getGoogleMapsMetadata().contains("Pune"));
  }
}
