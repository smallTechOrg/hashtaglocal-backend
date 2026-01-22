package org.smalltech.hashtaglocal_backend.service.discovery;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.smalltech.hashtaglocal_backend.dto.RawDiscoveryDTO;
import org.smalltech.hashtaglocal_backend.entity.DiscoveredLocality;
import org.smalltech.hashtaglocal_backend.entity.LocalityDiscoveryRun;
import org.smalltech.hashtaglocal_backend.entity.RawLocalityDiscovery;
import org.smalltech.hashtaglocal_backend.repository.DiscoveredLocalityRepository;
import org.smalltech.hashtaglocal_backend.repository.LocalityDiscoveryRunRepository;
import org.smalltech.hashtaglocal_backend.repository.RawLocalityDiscoveryRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Orchestrates the complete locality discovery workflow.
 *
 * Phase 1: Discovery 1. Create discovery run 2. Query GeoNames, OSM, IndiaPost
 * 3. Save all raw discoveries 4. Deduplicate and score 5. Save deduplicated
 * results
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class LocalityDiscoveryOrchestrator {

	private final LocalityDiscoveryRunRepository discoveryRunRepository;
	private final RawLocalityDiscoveryRepository rawDiscoveryRepository;
	private final DiscoveredLocalityRepository discoveredLocalityRepository;
	private final GeoNamesDiscoveryService geonamesService;
	private final OSMDiscoveryService osmService;

	@Transactional
	public LocalityDiscoveryRun discoverCities(String countryCode) {
		return discover(countryCode, "CITY");
	}

	@Transactional
	public LocalityDiscoveryRun discoverTowns(String countryCode) {
		return discover(countryCode, "TOWN");
	}

	@Transactional
	public LocalityDiscoveryRun discoverDistricts(String countryCode) {
		return discover(countryCode, "DISTRICT");
	}

	@Transactional
	public LocalityDiscoveryRun discoverAll(String countryCode) {
		// Create a single discovery run for all locality types to allow dedup across
		// city/town/district
		LocalityDiscoveryRun run = LocalityDiscoveryRun.builder().countryCode(countryCode)
				.status(LocalityDiscoveryRun.DiscoveryStatus.IN_PROGRESS).startedAt(LocalDateTime.now())
				.totalRawDiscoveries(0).geonamesCount(0).osmCount(0).indiaPostCount(0).build();

		run = discoveryRunRepository.save(run);
		log.info("📡 Starting ALL discovery for {}", countryCode);

		try {
			int geonamesCount = 0;
			int osmCount = 0;

			// GeoNames: city, town, district
			geonamesCount += saveRawDiscoveries(run, queryGeoNames("CITY", countryCode));
			geonamesCount += saveRawDiscoveries(run, queryGeoNames("TOWN", countryCode));
			geonamesCount += saveRawDiscoveries(run, queryGeoNames("DISTRICT", countryCode));

			// OSM: city, town, district
			osmCount += saveRawDiscoveries(run, queryOSM("CITY", countryCode));
			osmCount += saveRawDiscoveries(run, queryOSM("TOWN", countryCode));
			osmCount += saveRawDiscoveries(run, queryOSM("DISTRICT", countryCode));

			run.setGeonamesCount(geonamesCount);
			run.setOsmCount(osmCount);
			run.setTotalRawDiscoveries(geonamesCount + osmCount);

			// Deduplicate across all types in one pass
			deduplicateAndSave(run);

			run.setStatus(LocalityDiscoveryRun.DiscoveryStatus.COMPLETED);
			run.setCompletedAt(LocalDateTime.now());
			log.info("✅ ALL discovery completed for {}", countryCode);

		} catch (Exception e) {
			run.setStatus(LocalityDiscoveryRun.DiscoveryStatus.FAILED);
			run.setErrorMessage(e.getMessage());
			log.error("❌ ALL discovery failed for {}: {}", countryCode, e.getMessage());
		}

		return discoveryRunRepository.save(run);
	}

	private LocalityDiscoveryRun discover(String countryCode, String localityType) {
		// Create discovery run
		LocalityDiscoveryRun run = LocalityDiscoveryRun.builder().countryCode(countryCode)
				.status(LocalityDiscoveryRun.DiscoveryStatus.IN_PROGRESS).startedAt(LocalDateTime.now())
				.totalRawDiscoveries(0).geonamesCount(0).osmCount(0).indiaPostCount(0).build();

		run = discoveryRunRepository.save(run);
		log.info("📡 Starting {} discovery for {}", localityType, countryCode);

		try {
			// Query GeoNames
			List<RawDiscoveryDTO> geonamesResults = queryGeoNames(localityType, countryCode);
			int geonamesCount = saveRawDiscoveries(run, geonamesResults);
			run.setGeonamesCount(geonamesCount);

			// Query OSM
			List<RawDiscoveryDTO> osmResults = queryOSM(localityType, countryCode);
			int osmCount = saveRawDiscoveries(run, osmResults);
			run.setOsmCount(osmCount);

			// Total raw discoveries
			run.setTotalRawDiscoveries(geonamesCount + osmCount);

			// Deduplicate and save discovered localities
			deduplicateAndSave(run);

			// Mark as completed
			run.setStatus(LocalityDiscoveryRun.DiscoveryStatus.COMPLETED);
			run.setCompletedAt(LocalDateTime.now());
			log.info("✅ {} discovery completed for {}", localityType, countryCode);

		} catch (Exception e) {
			run.setStatus(LocalityDiscoveryRun.DiscoveryStatus.FAILED);
			run.setErrorMessage(e.getMessage());
			log.error("❌ {} discovery failed for {}: {}", localityType, countryCode, e.getMessage());
		}

		return discoveryRunRepository.save(run);
	}

	private List<RawDiscoveryDTO> queryGeoNames(String localityType, String countryCode) {
		try {
			return switch (localityType) {
				case "CITY" -> geonamesService.discoverCities(countryCode);
				case "TOWN" -> geonamesService.discoverTowns(countryCode);
				case "DISTRICT" -> geonamesService.discoverDistricts(countryCode);
				default -> new ArrayList<>();
			};
		} catch (Exception e) {
			log.error("GeoNames {} query failed: {}", localityType, e.getMessage());
			return new ArrayList<>();
		}
	}

	private List<RawDiscoveryDTO> queryOSM(String localityType, String countryCode) {
		try {
			return switch (localityType) {
				case "CITY" -> osmService.discoverCities(countryCode);
				case "TOWN" -> osmService.discoverTowns(countryCode);
				case "DISTRICT" -> osmService.discoverDistricts(countryCode);
				default -> new ArrayList<>();
			};
		} catch (Exception e) {
			log.error("OSM {} query failed: {}", localityType, e.getMessage());
			return new ArrayList<>();
		}
	}

	private int saveRawDiscoveries(LocalityDiscoveryRun run, List<RawDiscoveryDTO> discoveries) {
		for (RawDiscoveryDTO dto : discoveries) {
			RawLocalityDiscovery raw = RawLocalityDiscovery.builder().discoveryRun(run)
					.source(RawLocalityDiscovery.DiscoverySource.valueOf(dto.getSource())).name(dto.getName())
					.state(dto.getState()).countryCode(dto.getCountryCode())
					.localityType(RawLocalityDiscovery.LocalityType.valueOf(dto.getLocalityType()))
					.sourceMetadata(dto.getSourceMetadata()).build();
			rawDiscoveryRepository.save(raw);
		}
		return discoveries.size();
	}

	private void deduplicateAndSave(LocalityDiscoveryRun run) {
		log.info("🔀 Deduplicating discoveries...");

		// Get all raw discoveries for this run only
		List<RawLocalityDiscovery> rawDiscoveries = rawDiscoveryRepository.findByDiscoveryRun(run);

		// Group by normalized name + state (ignores locality type to merge city/town
		// duplicates)
		Map<String, DeduplicationEntry> dedupMap = new HashMap<>();
		Map<RawLocalityDiscovery.LocalityType, Integer> typePriority = Map.of(RawLocalityDiscovery.LocalityType.CITY, 1,
				RawLocalityDiscovery.LocalityType.TOWN, 2, RawLocalityDiscovery.LocalityType.DISTRICT, 3,
				RawLocalityDiscovery.LocalityType.UNKNOWN, 4);

		for (RawLocalityDiscovery raw : rawDiscoveries) {
			String safeName = raw.getName() == null ? "" : raw.getName().toLowerCase().trim();
			String safeState = raw.getState() == null ? "unknown" : raw.getState().toLowerCase().trim();
			String key = (safeName + "_" + safeState).replaceAll("\\s+", "_");

			DeduplicationEntry entry = dedupMap.computeIfAbsent(key, k -> new DeduplicationEntry());
			if (entry.officialName == null) {
				entry.officialName = raw.getName();
				entry.state = raw.getState();
				entry.countryCode = raw.getCountryCode();
				entry.localityType = raw.getLocalityType();
			}
			// Prefer higher-priority locality type (CITY > TOWN > DISTRICT > UNKNOWN)
			int currentPriority = typePriority.getOrDefault(entry.localityType, 10);
			int newPriority = typePriority.getOrDefault(raw.getLocalityType(), 10);
			if (newPriority < currentPriority) {
				entry.localityType = raw.getLocalityType();
			}
			entry.alternateNames.add(raw.getName());
			entry.sources.add(raw.getSource().toString());
		}

		// Save deduplicated localities
		for (DeduplicationEntry entry : dedupMap.values()) {
			DiscoveredLocality locality = DiscoveredLocality.builder().discoveryRun(run)
					.officialName(entry.officialName).alternateNames(new ArrayList<>(entry.alternateNames))
					.state(entry.state).countryCode(entry.countryCode)
					.localityType(DiscoveredLocality.LocalityType.valueOf(entry.localityType.toString()))
					.sources(new ArrayList<>(entry.sources)).confidenceScore(entry.sources.size()).build();
			discoveredLocalityRepository.save(locality);
		}

		log.info("✅ Deduplicated {} unique localities", dedupMap.size());
	}

	private static class DeduplicationEntry {
		String officialName;
		String state;
		String countryCode;
		RawLocalityDiscovery.LocalityType localityType;
		java.util.Set<String> alternateNames = new java.util.LinkedHashSet<>();
		java.util.Set<String> sources = new java.util.LinkedHashSet<>();
	}
}
