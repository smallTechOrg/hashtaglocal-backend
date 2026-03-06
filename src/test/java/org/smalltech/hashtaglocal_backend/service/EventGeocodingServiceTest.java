package org.smalltech.hashtaglocal_backend.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.fasterxml.jackson.annotation.JsonProperty;
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
import org.smalltech.hashtaglocal_backend.repository.EventRepository;

/**
 * Unit tests for {@link EventGeocodingService} geocoding logic.
 *
 * <p>The service queries for un-geocoded events (location_id IS NULL), calls the Google Maps API
 * for each address, creates a Location row, and links it back to the event. These tests verify that
 * flow without hitting the database or the real API.
 *
 * <p>Locality linking: after geocoding, {@code locationService.relinkLocalities()} is always called
 * as a final step to attach a Locality polygon to any Location rows whose locality_id is still
 * null. The tests verify the count is propagated in {@link
 * EventGeocodingService.GeocodingResult#localitiesLinked()} and that the call happens even when
 * geocoding fails or there are no events.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("EventGeocodingService — geocoding logic")
class EventGeocodingServiceTest {

  @Mock private EventRepository eventRepository;
  @Mock private GoogleMapsGeocodingService geocodingService;
  @Mock private LocationService locationService;

  @InjectMocks private EventGeocodingService geocodingService2;

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
  // Parameterized: address → lat/lng + locality — test cases live in geocoding-test-cases.json
  // ---------------------------------------------------------------------------

  private static final ObjectMapper MAPPER = new ObjectMapper();

  record GeocodingTestCase(
      String address,
      double lat,
      double lng,
      String city,
      String name,
      String locality,
      @JsonProperty("localities_linked") int localitiesLinked) {}

  record SkipScenario(
      String scenario,
      String description,
      @JsonProperty("repository_returns_count") int repositoryReturnsCount,
      @JsonProperty("expected_google_maps_calls") int expectedGoogleMapsCalls) {}

  static Stream<Arguments> geocodingTestCases() throws IOException {
    List<GeocodingTestCase> cases =
        MAPPER.convertValue(
            MAPPER
                .readTree(
                    EventGeocodingServiceTest.class.getResourceAsStream(
                        "/geocoding-test-cases.json"))
                .get("address_geocoding"),
            new TypeReference<>() {});
    return cases.stream()
        .map(
            tc ->
                Arguments.of(
                    tc.address(),
                    tc.lat(),
                    tc.lng(),
                    tc.city(),
                    tc.name(),
                    tc.locality(),
                    tc.localitiesLinked()));
  }

  static Stream<Arguments> skipScenarios() throws IOException {
    List<SkipScenario> cases =
        MAPPER.convertValue(
            MAPPER
                .readTree(
                    EventGeocodingServiceTest.class.getResourceAsStream(
                        "/geocoding-test-cases.json"))
                .get("skip_scenarios"),
            new TypeReference<>() {});
    return cases.stream().map(tc -> Arguments.of(tc.scenario(), tc.description()));
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("geocodingTestCases")
  @DisplayName("Links correct lat/lng and locality count to event for address")
  void linksCorrectCoordinatesToEvent(
      String address,
      double expectedLat,
      double expectedLng,
      String city,
      String name,
      String locality,
      int expectedLocalitiesLinked)
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
    when(locationService.relinkLocalities()).thenReturn(expectedLocalitiesLinked);

    EventGeocodingService.GeocodingResult result = geocodingService2.run();

    assertEquals(1, result.success());
    assertEquals(0, result.failed());
    assertEquals(
        expectedLocalitiesLinked,
        result.localitiesLinked(),
        "localitiesLinked should match — locality=" + locality);
    verify(eventRepository).save(argThat(e -> savedLocation.equals(e.getLocation())));
  }

  // ---------------------------------------------------------------------------
  // Parameterized: skip scenarios — already-geocoded events, no events in DB
  // ---------------------------------------------------------------------------

  @ParameterizedTest(name = "{0}")
  @MethodSource("skipScenarios")
  @DisplayName("Google Maps API is never called when no events need geocoding")
  void googleMapsNotCalledForSkipScenario(String scenario, String description) {
    // repository returns empty list — simulates all events already having a location
    when(eventRepository.findByLocationIsNullAndAddressIsNotNull()).thenReturn(List.of());

    geocodingService2.run();

    verify(geocodingService, never()).forwardGeocode(anyString());
    verify(locationService, never()).createAndSaveLocation(any(), any(), any(), any());
    verify(eventRepository, never()).save(any());
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

    EventGeocodingService.GeocodingResult result = geocodingService2.run();

    assertEquals(1, result.total());
    assertEquals(1, result.success());
    assertEquals(0, result.failed());
    verify(eventRepository).save(argThat(e -> savedLocation.equals(e.getLocation())));
  }

  @Test
  @DisplayName("Counts as failed when Google Maps returns no result — event is left un-geocoded")
  void countsFailedWhenGeocodingReturnsNull() {
    EventEntity event = eventWithAddress(1L, "Unknown Location XYZ");
    when(eventRepository.findByLocationIsNullAndAddressIsNotNull()).thenReturn(List.of(event));
    when(geocodingService.forwardGeocode(anyString())).thenReturn(null);

    EventGeocodingService.GeocodingResult result = geocodingService2.run();

    assertEquals(1, result.total());
    assertEquals(0, result.success());
    assertEquals(1, result.failed());
    verify(eventRepository, never()).save(any());
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

    EventGeocodingService.GeocodingResult result = geocodingService2.run();

    assertEquals(0, result.success());
    assertEquals(1, result.failed());
    verify(eventRepository, never()).save(any());
  }

  @Test
  @DisplayName("Returns total=0, success=0, failed=0 when there are no un-geocoded events")
  void returnsZeroCountsWhenNoEventsNeedGeocoding() {
    when(eventRepository.findByLocationIsNullAndAddressIsNotNull()).thenReturn(List.of());

    EventGeocodingService.GeocodingResult result = geocodingService2.run();

    assertEquals(0, result.total());
    assertEquals(0, result.success());
    assertEquals(0, result.failed());
    verifyNoInteractions(geocodingService);
    verify(locationService).relinkLocalities();
    verify(locationService, never()).createAndSaveLocation(any(), any(), any(), any());
  }

  // ---------------------------------------------------------------------------
  // Locality linking
  // ---------------------------------------------------------------------------

  @Test
  @DisplayName("localitiesLinked count from relinkLocalities() is propagated in result")
  void localitiesLinkedCountIsPropagatedInResult() {
    EventEntity event = eventWithAddress(1L, "Lalbagh Main Gate, Bengaluru");
    when(eventRepository.findByLocationIsNullAndAddressIsNotNull()).thenReturn(List.of(event));

    LocationMetadataDTO metadata =
        LocationMetadataDTO.builder().city("Bengaluru").name("Lalbagh").build();
    when(geocodingService.forwardGeocode(anyString()))
        .thenReturn(
            new GoogleMapsGeocodingService.ForwardGeocodeResult(12.9507, 77.5848, metadata));
    when(geocodingService.metadataToMap(any())).thenReturn(Map.of("city", "Bengaluru"));
    when(locationService.createAndSaveLocation(any(), any(), any(), any()))
        .thenReturn(Location.builder().id(10L).name("Lalbagh").build());
    when(locationService.relinkLocalities()).thenReturn(3);

    EventGeocodingService.GeocodingResult result = geocodingService2.run();

    assertEquals(3, result.localitiesLinked());
  }

  @Test
  @DisplayName("relinkLocalities() is called even when all geocoding fails")
  void relinkLocalitiesIsCalledEvenWhenAllGeocodingFails() {
    when(eventRepository.findByLocationIsNullAndAddressIsNotNull())
        .thenReturn(
            List.of(
                eventWithAddress(1L, "Bad Address One"), eventWithAddress(2L, "Bad Address Two")));
    when(geocodingService.forwardGeocode(anyString())).thenReturn(null);

    geocodingService2.run();

    verify(locationService).relinkLocalities();
  }

  @Test
  @DisplayName("localitiesLinked is 0 when relinkLocalities() returns 0 — localities table empty")
  void localitiesLinkedIsZeroWhenNoLocalitiesExist() {
    EventEntity event = eventWithAddress(1L, "Juhu Beach, Mumbai");
    when(eventRepository.findByLocationIsNullAndAddressIsNotNull()).thenReturn(List.of(event));

    LocationMetadataDTO metadata =
        LocationMetadataDTO.builder().city("Mumbai").name("Juhu Beach").build();
    when(geocodingService.forwardGeocode(anyString()))
        .thenReturn(
            new GoogleMapsGeocodingService.ForwardGeocodeResult(19.0896, 72.8266, metadata));
    when(geocodingService.metadataToMap(any())).thenReturn(Map.of("city", "Mumbai"));
    when(locationService.createAndSaveLocation(any(), any(), any(), any()))
        .thenReturn(Location.builder().id(11L).name("Juhu Beach").build());
    when(locationService.relinkLocalities()).thenReturn(0);

    EventGeocodingService.GeocodingResult result = geocodingService2.run();

    assertEquals(0, result.localitiesLinked(), "No localities in DB — nothing can be linked");
  }

  @Test
  @DisplayName("relinkLocalities() is called exactly once per run regardless of event count")
  void relinkLocalitiesIsCalledExactlyOncePerRun() {
    when(eventRepository.findByLocationIsNullAndAddressIsNotNull())
        .thenReturn(
            List.of(
                eventWithAddress(1L, "Lalbagh Main Gate, Bengaluru"),
                eventWithAddress(2L, "Cubbon Park, Bengaluru")));

    LocationMetadataDTO metadata =
        LocationMetadataDTO.builder().city("Bengaluru").name("Bengaluru Location").build();
    when(geocodingService.forwardGeocode(anyString()))
        .thenReturn(new GoogleMapsGeocodingService.ForwardGeocodeResult(12.97, 77.59, metadata));
    when(geocodingService.metadataToMap(any())).thenReturn(Map.of());
    when(locationService.createAndSaveLocation(any(), any(), any(), any()))
        .thenReturn(Location.builder().id(10L).build());

    geocodingService2.run();

    verify(locationService, times(1)).relinkLocalities();
  }
}
