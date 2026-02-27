package org.smalltech.hashtaglocal_backend.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.smalltech.hashtaglocal_backend.dto.LocationMetadataDTO;
import org.smalltech.hashtaglocal_backend.entity.EventEntity;
import org.smalltech.hashtaglocal_backend.entity.Location;
import org.smalltech.hashtaglocal_backend.job.EventGeocodingJob;
import org.smalltech.hashtaglocal_backend.repository.EventRepository;

/**
 * Unit tests for {@link EventGeocodingJob} geocoding logic.
 *
 * <p>The job queries for un-geocoded events (location_id IS NULL), calls the Google Maps API for
 * each address, creates a Location row, and links it back to the event. These tests verify that
 * flow without hitting the database or the real API.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("EventGeocodingJob — geocoding logic")
class EventGeocodingJobTest {

  @Mock private EventRepository eventRepository;
  @Mock private GoogleMapsGeocodingService geocodingService;
  @Mock private LocationService locationService;

  @InjectMocks private EventGeocodingJob geocodingJob;

  private EventEntity eventWithAddress(long id, String address) {
    return EventEntity.builder()
        .id(id)
        .eventName("Event " + id)
        .organisation("Org")
        .address(address)
        .startTime(LocalDateTime.now())
        .build();
  }

  // ---------------------------------------------------------------------------
  // Parameterized: address → lat/lng — test cases live in geocoding-test-cases.json
  // ---------------------------------------------------------------------------

  record GeocodingTestCase(String address, double lat, double lng, String city, String name) {}

  static Stream<Arguments> geocodingTestCases() throws IOException {
    List<GeocodingTestCase> cases =
        new ObjectMapper()
            .readValue(
                EventGeocodingJobTest.class.getResourceAsStream("/geocoding-test-cases.json"),
                new TypeReference<>() {});
    return cases.stream()
        .map(tc -> Arguments.of(tc.address(), tc.lat(), tc.lng(), tc.city(), tc.name()));
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("geocodingTestCases")
  @DisplayName("Links correct lat/lng to event for address")
  void linksCorrectCoordinatesToEvent(
      String address, double expectedLat, double expectedLng, String city, String name)
      throws Exception {
    EventEntity event = eventWithAddress(1L, address);
    when(eventRepository.findByLocationIsNullAndAddressIsNotNull()).thenReturn(List.of(event));

    LocationMetadataDTO metadata = LocationMetadataDTO.builder().city(city).name(name).build();
    GoogleMapsGeocodingService.ForwardGeocodeResult geocodeResult =
        new GoogleMapsGeocodingService.ForwardGeocodeResult(expectedLat, expectedLng, metadata);
    when(geocodingService.forwardGeocode(address)).thenReturn(geocodeResult);
    when(geocodingService.metadataToMap(metadata)).thenReturn(Map.of("city", city));

    Location savedLocation = Location.builder().id(10L).name(name).build();
    when(locationService.createAndSaveLocation(eq(expectedLat), eq(expectedLng), any(), eq(name)))
        .thenReturn(savedLocation);

    EventGeocodingJob.GeocodingJobResult result = geocodingJob.run();

    assertEquals(1, result.success());
    assertEquals(0, result.failed());
    verify(eventRepository).save(argThat(e -> savedLocation.equals(e.getLocation())));
  }

  // ---------------------------------------------------------------------------
  // Failure paths
  // ---------------------------------------------------------------------------

  @Test
  @DisplayName("Geocodes event — creates Location row and links it back to the event")
  void geocodesEventAndLinksLocation() {
    EventEntity event = eventWithAddress(1L, "Lalbagh Main Gate, Bengaluru");
    when(eventRepository.findByLocationIsNullAndAddressIsNotNull()).thenReturn(List.of(event));

    LocationMetadataDTO metadata =
        LocationMetadataDTO.builder().city("Bengaluru").name("Lalbagh").build();
    GoogleMapsGeocodingService.ForwardGeocodeResult geocodeResult =
        new GoogleMapsGeocodingService.ForwardGeocodeResult(12.9716, 77.5946, metadata);
    when(geocodingService.forwardGeocode("Lalbagh Main Gate, Bengaluru")).thenReturn(geocodeResult);
    when(geocodingService.metadataToMap(metadata)).thenReturn(Map.of("city", "Bengaluru"));

    Location savedLocation = Location.builder().id(10L).name("Lalbagh").build();
    when(locationService.createAndSaveLocation(eq(12.9716), eq(77.5946), any(), eq("Lalbagh")))
        .thenReturn(savedLocation);

    EventGeocodingJob.GeocodingJobResult result = geocodingJob.run();

    assertEquals(1, result.total());
    assertEquals(1, result.success());
    assertEquals(0, result.failed());
    // event should be saved with the location linked
    verify(eventRepository).save(argThat(e -> savedLocation.equals(e.getLocation())));
  }

  @Test
  @DisplayName("Counts as failed when Google Maps returns no result — event is left un-geocoded")
  void countsFailedWhenGeocodingReturnsNull() {
    EventEntity event = eventWithAddress(1L, "Unknown Location XYZ");
    when(eventRepository.findByLocationIsNullAndAddressIsNotNull()).thenReturn(List.of(event));
    when(geocodingService.forwardGeocode(anyString())).thenReturn(null);

    EventGeocodingJob.GeocodingJobResult result = geocodingJob.run();

    assertEquals(1, result.total());
    assertEquals(0, result.success());
    assertEquals(1, result.failed());
    verify(eventRepository, never()).save(any()); // event must not be saved
  }

  @Test
  @DisplayName("Counts as failed when LocationService returns null — event is left un-geocoded")
  void countsFailedWhenLocationServiceReturnsNull() {
    EventEntity event = eventWithAddress(1L, "Some Address");
    when(eventRepository.findByLocationIsNullAndAddressIsNotNull()).thenReturn(List.of(event));

    GoogleMapsGeocodingService.ForwardGeocodeResult geocodeResult =
        new GoogleMapsGeocodingService.ForwardGeocodeResult(
            12.9, 77.5, LocationMetadataDTO.builder().build());
    when(geocodingService.forwardGeocode(anyString())).thenReturn(geocodeResult);
    when(geocodingService.metadataToMap(any())).thenReturn(Map.of());
    when(locationService.createAndSaveLocation(any(), any(), any(), any())).thenReturn(null);

    EventGeocodingJob.GeocodingJobResult result = geocodingJob.run();

    assertEquals(0, result.success());
    assertEquals(1, result.failed());
    verify(eventRepository, never()).save(any());
  }

  @Test
  @DisplayName("Returns total=0, success=0, failed=0 when there are no un-geocoded events")
  void returnsZeroCountsWhenNoEventsNeedGeocoding() {
    when(eventRepository.findByLocationIsNullAndAddressIsNotNull()).thenReturn(List.of());

    EventGeocodingJob.GeocodingJobResult result = geocodingJob.run();

    assertEquals(0, result.total());
    assertEquals(0, result.success());
    assertEquals(0, result.failed());
    verifyNoInteractions(geocodingService, locationService);
  }
}
