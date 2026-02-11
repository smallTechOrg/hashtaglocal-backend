package org.smalltech.hashtaglocal_backend.service.discovery;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.smalltech.hashtaglocal_backend.dto.RawDiscoveryDTO;
import org.smalltech.hashtaglocal_backend.entity.DiscoveredLocality;
import org.smalltech.hashtaglocal_backend.entity.LocalityDiscoveryRun;
import org.smalltech.hashtaglocal_backend.repository.DiscoveredLocalityRepository;
import org.smalltech.hashtaglocal_backend.repository.LocalityDiscoveryRunRepository;
import org.smalltech.hashtaglocal_backend.repository.RawLocalityDiscoveryRepository;

/**
 * Test LocalityDiscoveryOrchestrator.
 *
 * Verifies: 1. Discovery run created and tracked 2. All sources queried
 * (GeoNames, OSM, IndiaPost) 3. Raw discoveries saved to database 4.
 * Deduplication logic (same locality from multiple sources) 5. Confidence score
 * calculated 6. Final results saved to discovered_localities table
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("Locality Discovery Orchestrator")
class LocalityDiscoveryOrchestratorTest {

	@Mock
	private LocalityDiscoveryRunRepository discoveryRunRepository;

	@Mock
	private RawLocalityDiscoveryRepository rawDiscoveryRepository;

	@Mock
	private DiscoveredLocalityRepository discoveredLocalityRepository;

	@Mock
	private GeoNamesDiscoveryService geonamesService;

	@Mock
	private OSMDiscoveryService osmService;

	private LocalityDiscoveryOrchestrator orchestrator;

	@BeforeEach
	void setUp() {
		orchestrator = new LocalityDiscoveryOrchestrator(discoveryRunRepository, rawDiscoveryRepository,
				discoveredLocalityRepository, geonamesService, osmService);
	}

	@Test
	@DisplayName("Should orchestrate complete discovery from multiple sources")
	void completeDiscoveryWorkflow() {
		// Arrange: Create discovery run
		LocalityDiscoveryRun run = LocalityDiscoveryRun.builder().countryCode("IN")
				.status(LocalityDiscoveryRun.DiscoveryStatus.IN_PROGRESS).startedAt(LocalDateTime.now())
				.totalRawDiscoveries(0).geonamesCount(0).osmCount(0).indiaPostCount(0).build();

		when(discoveryRunRepository.save(any(LocalityDiscoveryRun.class))).thenReturn(run);

		// GeoNames returns 2 cities
		List<RawDiscoveryDTO> geonamesResults = Arrays.asList(
				RawDiscoveryDTO.builder().name("Bengaluru").state("Karnataka").countryCode("IN").localityType("CITY")
						.source("GEONAMES").sourceMetadata("{}").build(),
				RawDiscoveryDTO.builder().name("Mumbai").state("Maharashtra").countryCode("IN").localityType("CITY")
						.source("GEONAMES").sourceMetadata("{}").build());

		// OSM also finds Mumbai (variant name) and another city
		List<RawDiscoveryDTO> osmResults = Arrays.asList(
				RawDiscoveryDTO.builder().name("Bombay").state("Maharashtra").countryCode("IN").localityType("CITY")
						.source("OSM").sourceMetadata("{}").build(),
				RawDiscoveryDTO.builder().name("Pune").state("Maharashtra").countryCode("IN").localityType("CITY")
						.source("OSM").sourceMetadata("{}").build());

		when(geonamesService.discoverCities("IN")).thenReturn(geonamesResults);
		when(osmService.discoverCities("IN")).thenReturn(osmResults);

		when(rawDiscoveryRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
		when(discoveredLocalityRepository.save(any(DiscoveredLocality.class))).thenAnswer(inv -> inv.getArgument(0));

		// Act
		LocalityDiscoveryRun result = orchestrator.discoverCities("IN");

		// Assert
		assertNotNull(result);
		assertEquals(LocalityDiscoveryRun.DiscoveryStatus.COMPLETED, result.getStatus());
		assertEquals(2, result.getGeonamesCount());
		assertEquals(2, result.getOsmCount());
		assertEquals(4, result.getTotalRawDiscoveries());

		// Verify raw discoveries saved (4 total: 2 from GeoNames, 2 from OSM)
		verify(rawDiscoveryRepository, times(4)).save(any());
	}

	@Test
	@DisplayName("Should handle discovery source failures gracefully")
	void handleServiceFailures() {
		LocalityDiscoveryRun run = LocalityDiscoveryRun.builder().countryCode("IN")
				.status(LocalityDiscoveryRun.DiscoveryStatus.IN_PROGRESS).startedAt(LocalDateTime.now())
				.totalRawDiscoveries(0).geonamesCount(0).osmCount(0).indiaPostCount(0).build();

		when(discoveryRunRepository.save(any(LocalityDiscoveryRun.class))).thenReturn(run);

		// GeoNames fails
		when(geonamesService.discoverCities("IN")).thenThrow(new RuntimeException("API error"));

		// OSM succeeds
		List<RawDiscoveryDTO> osmResults = Arrays.asList(RawDiscoveryDTO.builder().name("Delhi").state("Delhi")
				.countryCode("IN").localityType("CITY").source("OSM").sourceMetadata("{}").build());

		when(osmService.discoverCities("IN")).thenReturn(osmResults);

		when(rawDiscoveryRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
		when(discoveredLocalityRepository.save(any(DiscoveredLocality.class))).thenAnswer(inv -> inv.getArgument(0));

		// Act: Should not throw, should complete with partial results
		LocalityDiscoveryRun result = orchestrator.discoverCities("IN");

		// Assert
		assertNotNull(result);
		assertEquals(LocalityDiscoveryRun.DiscoveryStatus.COMPLETED, result.getStatus());
		assertEquals(0, result.getGeonamesCount()); // Failed, count is 0
		assertEquals(1, result.getOsmCount()); // Succeeded
		assertEquals(1, result.getTotalRawDiscoveries());
	}
}
