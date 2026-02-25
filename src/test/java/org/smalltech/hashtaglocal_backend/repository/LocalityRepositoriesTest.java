package org.smalltech.hashtaglocal_backend.repository;

import static org.junit.jupiter.api.Assertions.*;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.Polygon;
import org.smalltech.hashtaglocal_backend.entity.DiscoveredLocality;
import org.smalltech.hashtaglocal_backend.entity.ImportJob;
import org.smalltech.hashtaglocal_backend.entity.LocalityDiscoveryRun;
import org.smalltech.hashtaglocal_backend.entity.LocalityImportStatus;
import org.smalltech.hashtaglocal_backend.entity.RawLocalityDiscovery;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;

/**
 * Integration test for locality discovery and import workflow.
 *
 * <p>Tests the complete flow: 1. Discovery run created and tracked 2. Raw discoveries saved from
 * multiple sources 3. Discovered localities deduplicated 4. Import jobs and status tracking
 *
 * <p>This is a high-level test showing how to debug the entire workflow in isolation.
 */
@DataJpaTest
@ActiveProfiles("test")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@DisplayName("Locality Discovery & Import Integration")
class LocalityRepositoriesTest {

  @Autowired private LocalityDiscoveryRunRepository discoveryRunRepository;

  @Autowired private RawLocalityDiscoveryRepository rawDiscoveryRepository;

  @Autowired private DiscoveredLocalityRepository discoveredLocalityRepository;

  @Autowired private ImportJobRepository importJobRepository;

  @Autowired private LocalityImportStatusRepository importStatusRepository;

  @Autowired private LocalityRepository localityRepository;

  private LocalityDiscoveryRun run;
  private ImportJob job;

  @BeforeEach
  void setUp() {
    run =
        LocalityDiscoveryRun.builder()
            .countryCode("IN")
            .status(LocalityDiscoveryRun.DiscoveryStatus.IN_PROGRESS)
            .startedAt(LocalDateTime.now())
            .totalRawDiscoveries(0)
            .geonamesCount(0)
            .osmCount(0)
            .indiaPostCount(0)
            .build();
    run = discoveryRunRepository.save(run);

    job =
        ImportJob.builder()
            .startedAt(LocalDateTime.now())
            .totalLocalities(100)
            .status(ImportJob.ImportJobStatus.RUNNING)
            .build();
    job = importJobRepository.save(job);
  }

  @Nested
  @DisplayName("Discovery Run Workflow")
  class DiscoveryRunWorkflow {

    @Test
    @DisplayName("Should create discovery run and track status")
    void completeDiscoveryRun() {
      // Verify discovery run created
      LocalityDiscoveryRun found = discoveryRunRepository.findById(run.getId()).orElse(null);
      assertNotNull(found);
      assertEquals("IN", found.getCountryCode());
      assertEquals(LocalityDiscoveryRun.DiscoveryStatus.IN_PROGRESS, found.getStatus());

      // Complete the run
      run.setStatus(LocalityDiscoveryRun.DiscoveryStatus.COMPLETED);
      run.setCompletedAt(LocalDateTime.now());
      discoveryRunRepository.save(run);

      // Verify status updated
      LocalityDiscoveryRun updated = discoveryRunRepository.findById(run.getId()).orElse(null);
      assertEquals(LocalityDiscoveryRun.DiscoveryStatus.COMPLETED, updated.getStatus());
    }

    @Test
    @DisplayName("Should query discovery runs by country")
    void queryByCountry() {
      List<LocalityDiscoveryRun> runs = discoveryRunRepository.findByCountryCode("IN");
      assertFalse(runs.isEmpty());
      assertTrue(runs.stream().anyMatch(r -> r.getId().equals(run.getId())));
    }
  }

  @Nested
  @DisplayName("Raw Discovery Integration")
  class RawDiscoveryWorkflow {

    @Test
    @DisplayName("Should collect raw discoveries from multiple sources")
    void collectFromMultipleSources() {
      // Simulate GeoNames discovery
      RawLocalityDiscovery geonames =
          RawLocalityDiscovery.builder()
              .discoveryRun(run)
              .source(RawLocalityDiscovery.DiscoverySource.GEONAMES)
              .name("Mumbai")
              .state("Maharashtra")
              .countryCode("IN")
              .localityType(RawLocalityDiscovery.LocalityType.CITY)
              .sourceMetadata("{\"feature_code\": \"PPLA\"}")
              .build();

      // Simulate OSM discovery (same locality, variant name)
      RawLocalityDiscovery osm =
          RawLocalityDiscovery.builder()
              .discoveryRun(run)
              .source(RawLocalityDiscovery.DiscoverySource.OSM)
              .name("Bombay")
              .state("Maharashtra")
              .countryCode("IN")
              .localityType(RawLocalityDiscovery.LocalityType.CITY)
              .sourceMetadata("{\"osm_id\": 1234}")
              .build();

      rawDiscoveryRepository.save(geonames);
      rawDiscoveryRepository.save(osm);

      // Query all for this run
      List<RawLocalityDiscovery> discoveries = rawDiscoveryRepository.findByDiscoveryRun(run);
      assertEquals(2, discoveries.size());

      // Query by source
      List<RawLocalityDiscovery> geonamesResults =
          rawDiscoveryRepository.findBySource(RawLocalityDiscovery.DiscoverySource.GEONAMES);
      assertTrue(geonamesResults.stream().anyMatch(d -> "Mumbai".equals(d.getName())));
    }
  }

  @Nested
  @DisplayName("Deduplication Workflow")
  class DeduplicationWorkflow {

    @Test
    @DisplayName("Should deduplicate multiple source discoveries into confidence score")
    void deduplicateWithConfidenceScore() {
      // Create raw discoveries
      RawLocalityDiscovery raw1 =
          RawLocalityDiscovery.builder()
              .discoveryRun(run)
              .source(RawLocalityDiscovery.DiscoverySource.GEONAMES)
              .name("Bangalore")
              .state("Karnataka")
              .countryCode("IN")
              .localityType(RawLocalityDiscovery.LocalityType.CITY)
              .sourceMetadata("{}")
              .build();

      RawLocalityDiscovery raw2 =
          RawLocalityDiscovery.builder()
              .discoveryRun(run)
              .source(RawLocalityDiscovery.DiscoverySource.OSM)
              .name("Bengaluru")
              .state("Karnataka")
              .countryCode("IN")
              .localityType(RawLocalityDiscovery.LocalityType.CITY)
              .sourceMetadata("{}")
              .build();

      rawDiscoveryRepository.save(raw1);
      rawDiscoveryRepository.save(raw2);

      // Create deduplicated entry with high confidence
      DiscoveredLocality discovered =
          DiscoveredLocality.builder()
              .discoveryRun(run)
              .officialName("Bengaluru")
              .alternateNames(Arrays.asList("Bangalore", "Bangaluru"))
              .state("Karnataka")
              .countryCode("IN")
              .localityType(DiscoveredLocality.LocalityType.CITY)
              .sources(Arrays.asList("geonames", "osm"))
              .confidenceScore(2) // 2 sources confirmed
              .build();

      DiscoveredLocality saved = discoveredLocalityRepository.save(discovered);
      assertEquals(2, saved.getConfidenceScore());
      assertEquals(2, saved.getSources().size());
    }

    @Test
    @DisplayName("Should filter by locality type")
    void filterByLocalityType() {
      // Create mixed types
      DiscoveredLocality city =
          DiscoveredLocality.builder()
              .discoveryRun(run)
              .officialName("Mumbai")
              .alternateNames(Arrays.asList())
              .state("Maharashtra")
              .countryCode("IN")
              .localityType(DiscoveredLocality.LocalityType.CITY)
              .sources(Arrays.asList("geonames"))
              .confidenceScore(1)
              .build();

      DiscoveredLocality town =
          DiscoveredLocality.builder()
              .discoveryRun(run)
              .officialName("Aurangabad")
              .alternateNames(Arrays.asList())
              .state("Maharashtra")
              .countryCode("IN")
              .localityType(DiscoveredLocality.LocalityType.TOWN)
              .sources(Arrays.asList("geonames"))
              .confidenceScore(1)
              .build();

      discoveredLocalityRepository.save(city);
      discoveredLocalityRepository.save(town);

      // Query only cities
      List<DiscoveredLocality> cities =
          discoveredLocalityRepository.findByLocalityType(DiscoveredLocality.LocalityType.CITY);
      assertTrue(
          cities.stream()
              .allMatch(l -> l.getLocalityType() == DiscoveredLocality.LocalityType.CITY));
    }
  }

  @Nested
  @DisplayName("Import & Validation Workflow")
  class ImportValidationWorkflow {

    @Test
    @DisplayName("Should track import job with multiple localities")
    void trackImportJobProgress() {
      // Create localities to import
      DiscoveredLocality discovered =
          DiscoveredLocality.builder()
              .discoveryRun(run)
              .officialName("Pune")
              .alternateNames(Arrays.asList())
              .state("Maharashtra")
              .countryCode("IN")
              .localityType(DiscoveredLocality.LocalityType.CITY)
              .sources(Arrays.asList("geonames"))
              .confidenceScore(1)
              .build();
      DiscoveredLocality saved = discoveredLocalityRepository.save(discovered);

      // Record successful import
      LocalityImportStatus successStatus =
          LocalityImportStatus.builder()
              .importJob(job)
              .discoveredLocality(saved)
              .localityName("Pune")
              .localityType(LocalityImportStatus.LocalityType.CITY)
              .state("Maharashtra")
              .importStatus(LocalityImportStatus.ImportStatus.SUCCESS)
              .attemptCount(1)
              .build();

      importStatusRepository.save(successStatus);

      // Query import progress
      List<LocalityImportStatus> jobStatuses = importStatusRepository.findByImportJob(job);
      assertTrue(
          jobStatuses.stream()
              .anyMatch(s -> s.getImportStatus() == LocalityImportStatus.ImportStatus.SUCCESS));
    }

    @Test
    @DisplayName("Should track failed imports with retry logic")
    void trackRetryLogic() {
      DiscoveredLocality discovered =
          DiscoveredLocality.builder()
              .discoveryRun(run)
              .officialName("Test City")
              .alternateNames(Arrays.asList())
              .state("Test State")
              .countryCode("IN")
              .localityType(DiscoveredLocality.LocalityType.CITY)
              .sources(Arrays.asList("geonames"))
              .confidenceScore(1)
              .build();
      DiscoveredLocality saved = discoveredLocalityRepository.save(discovered);

      // First attempt failed
      LocalityImportStatus failed =
          LocalityImportStatus.builder()
              .importJob(job)
              .discoveredLocality(saved)
              .localityName("Test City")
              .localityType(LocalityImportStatus.LocalityType.CITY)
              .state("Test State")
              .importStatus(LocalityImportStatus.ImportStatus.FAILED)
              .errorMessage("API rate limit exceeded")
              .attemptCount(1)
              .build();

      importStatusRepository.save(failed);

      // Retry and succeed
      failed.setImportStatus(LocalityImportStatus.ImportStatus.SUCCESS);
      failed.setAttemptCount(2);
      failed.setErrorMessage(null);
      importStatusRepository.save(failed);

      LocalityImportStatus updated = importStatusRepository.findById(failed.getId()).orElse(null);
      assertEquals(LocalityImportStatus.ImportStatus.SUCCESS, updated.getImportStatus());
      assertEquals(2, updated.getAttemptCount());
    }
  }

  @Nested
  @DisplayName("Spatial Lookup")
  class SpatialLookup {

    @Test
    @DisplayName("Should resolve locality containing Bangalore point")
    void resolvesBangaloreLocality() {
      double lat = 12.9629;
      double lng = 77.5775;

      // Rough bounding box around Bengaluru; coordinates are (lng, lat)
      Coordinate[] coords =
          new Coordinate[] {
            new Coordinate(77.35, 12.80),
            new Coordinate(77.80, 12.80),
            new Coordinate(77.80, 13.10),
            new Coordinate(77.35, 13.10),
            new Coordinate(77.35, 12.80)
          };
      GeometryFactory gf = new GeometryFactory();
      Polygon polygon = gf.createPolygon(coords);
      polygon.setSRID(4326);

      var bangalore =
          org.smalltech.hashtaglocal_backend.entity.Locality.builder()
              .hashtag("#bangalore")
              .name("Bangalore")
              .geoBoundary(polygon)
              .build();
      localityRepository.save(bangalore);

      var found = localityRepository.findContainingLocality(lat, lng);
      assertTrue(found.isPresent(), "Expected locality for Bangalore coordinates");
      assertEquals("#bangalore", found.get().getHashtag());
    }
  }
}
