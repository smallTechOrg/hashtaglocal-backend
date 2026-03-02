package org.smalltech.hashtaglocal_backend.job;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.smalltech.hashtaglocal_backend.entity.EventEntity;
import org.smalltech.hashtaglocal_backend.entity.Location;
import org.smalltech.hashtaglocal_backend.repository.EventRepository;
import org.smalltech.hashtaglocal_backend.service.GoogleMapsGeocodingService;
import org.smalltech.hashtaglocal_backend.service.LocationService;
import org.springframework.stereotype.Service;

/**
 * Job that forward-geocodes events whose {@code location_id} is still null.
 *
 * <p>For each event with a raw {@code address} and no linked {@link Location}, it calls the Google
 * Maps Geocoding API (address → lat/lng), creates a {@link Location} row, and sets the FK on the
 * event.
 *
 * <p>Rate-limited to ~10 requests/second to stay within Google Maps API quotas.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class EventGeocodingJob {

  private final EventRepository eventRepository;
  private final GoogleMapsGeocodingService geocodingService;
  private final LocationService locationService;

  private static final long DELAY_BETWEEN_REQUESTS_MS = 100;

  public GeocodingJobResult run() {
    List<EventEntity> events = eventRepository.findByLocationIsNullAndAddressIsNotNull();
    log.info("Found {} events without a geocoded location", events.size());

    int success = 0;
    int failed = 0;

    for (int i = 0; i < events.size(); i++) {
      EventEntity event = events.get(i);
      try {
        GoogleMapsGeocodingService.ForwardGeocodeResult result =
            geocodingService.forwardGeocode(event.getAddress());

        if (result == null) {
          log.warn(
              "No geocoding result for event id={}, address='{}'",
              event.getId(),
              event.getAddress());
          failed++;
          continue;
        }

        Location location =
            locationService.createAndSaveLocation(
                result.lat(),
                result.lng(),
                geocodingService.metadataToMap(result.metadata()),
                result.metadata().getName());

        if (location == null) {
          log.warn("Failed to create location for event id={}", event.getId());
          failed++;
          continue;
        }

        event.setLocation(location);
        eventRepository.save(event);
        success++;

        log.info("Geocoded event id={}: lat={}, lng={}", event.getId(), result.lat(), result.lng());

        if (i < events.size() - 1) {
          Thread.sleep(DELAY_BETWEEN_REQUESTS_MS);
        }

      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        log.error("Event geocoding job interrupted at {}/{}", i + 1, events.size());
        break;
      } catch (Exception e) {
        log.error("Error geocoding event id={}: {}", event.getId(), e.getMessage());
        failed++;
      }
    }

    // After geocoding, relink localities for any Location rows that still have locality_id = null.
    // This fixes locations created when the localities table was empty.
    int localitiesLinked = locationService.relinkLocalities();

    GeocodingJobResult jobResult =
        new GeocodingJobResult(events.size(), success, failed, localitiesLinked);
    log.info("Event geocoding job completed: {}", jobResult);
    return jobResult;
  }

  public record GeocodingJobResult(
      @Schema(example = "100") int total,
      @Schema(example = "95") int success,
      @Schema(example = "5") int failed,
      @Schema(example = "90") int localitiesLinked) {
    @Override
    public String toString() {
      return String.format(
          "Total=%d, Success=%d, Failed=%d, LocalitiesLinked=%d",
          total, success, failed, localitiesLinked);
    }
  }
}
