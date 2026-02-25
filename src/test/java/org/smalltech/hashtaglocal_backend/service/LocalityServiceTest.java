package org.smalltech.hashtaglocal_backend.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.Polygon;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.smalltech.hashtaglocal_backend.dto.LocalityDTO;
import org.smalltech.hashtaglocal_backend.entity.Locality;
import org.smalltech.hashtaglocal_backend.repository.LocalityRepository;

/** Unit tests for LocalityService. */
@DisplayName("LocalityService Tests")
class LocalityServiceTest {

  @Mock private LocalityRepository localityRepository;

  @InjectMocks private LocalityService localityService;

  private GeometryFactory geometryFactory;

  @BeforeEach
  void setup() {
    MockitoAnnotations.openMocks(this);
    geometryFactory = new GeometryFactory();
  }

  @Test
  @DisplayName("Should return all localities with converted polygons")
  void testGetAllLocalitiesWithPolygons_Success() {
    // Arrange
    Polygon polygon1 = createTestPolygon(77.6408, 12.9716, 77.6500, 12.9800);
    Polygon polygon2 = createTestPolygon(77.6100, 12.9352, 77.6200, 12.9400);

    Locality locality1 =
        Locality.builder()
            .id(1L)
            .hashtag("indiranagar")
            .name("Indiranagar")
            .geoBoundary(polygon1)
            .build();

    Locality locality2 =
        Locality.builder()
            .id(2L)
            .hashtag("koramangala")
            .name("Koramangala")
            .geoBoundary(polygon2)
            .build();

    List<Locality> mockLocalities = Arrays.asList(locality1, locality2);
    when(localityRepository.findAll()).thenReturn(mockLocalities);

    // Act
    List<LocalityDTO> result = localityService.getAllLocalitiesWithPolygons();

    // Assert
    assertNotNull(result);
    assertEquals(2, result.size());

    LocalityDTO dto1 = result.get(0);
    assertEquals(1L, dto1.getId());
    assertEquals("indiranagar", dto1.getHashtag());
    assertEquals("Indiranagar", dto1.getName());
    assertNotNull(dto1.getGeoBoundary());
    assertEquals("Polygon", dto1.getGeoBoundary().getType());
    assertNotNull(dto1.getGeoBoundary().getCoordinates());
    assertEquals(1, dto1.getGeoBoundary().getCoordinates().length); // One ring

    LocalityDTO dto2 = result.get(1);
    assertEquals(2L, dto2.getId());
    assertEquals("koramangala", dto2.getHashtag());

    verify(localityRepository, times(1)).findAll();
  }

  @Test
  @DisplayName("Should return empty list when no localities exist")
  void testGetAllLocalitiesWithPolygons_EmptyList() {
    // Arrange
    when(localityRepository.findAll()).thenReturn(Collections.emptyList());

    // Act
    List<LocalityDTO> result = localityService.getAllLocalitiesWithPolygons();

    // Assert
    assertNotNull(result);
    assertTrue(result.isEmpty());
    verify(localityRepository, times(1)).findAll();
  }

  @Test
  @DisplayName("Should correctly convert polygon coordinates to GeoJSON format")
  void testPolygonConversionToGeoJSON() {
    // Arrange
    Polygon polygon = createTestPolygon(77.6408, 12.9716, 77.6500, 12.9800);

    Locality locality =
        Locality.builder()
            .id(1L)
            .hashtag("test")
            .name("Test Locality")
            .geoBoundary(polygon)
            .build();

    when(localityRepository.findAll()).thenReturn(Collections.singletonList(locality));

    // Act
    List<LocalityDTO> result = localityService.getAllLocalitiesWithPolygons();

    // Assert
    assertNotNull(result);
    assertEquals(1, result.size());

    LocalityDTO.PolygonDTO polygonDTO = result.get(0).getGeoBoundary();
    assertNotNull(polygonDTO);
    assertEquals("Polygon", polygonDTO.getType());

    double[][][] coordinates = polygonDTO.getCoordinates();
    assertNotNull(coordinates);
    assertEquals(1, coordinates.length); // One ring (exterior)
    assertTrue(coordinates[0].length >= 4); // At least 4 points (closed ring)

    // Verify it's in [lng, lat] format (GeoJSON standard)
    double[] firstPoint = coordinates[0][0];
    assertEquals(2, firstPoint.length);
    assertTrue(firstPoint[0] >= -180 && firstPoint[0] <= 180); // longitude range
    assertTrue(firstPoint[1] >= -90 && firstPoint[1] <= 90); // latitude range
  }

  @Test
  @DisplayName("Should handle locality with null polygon gracefully")
  void testGetAllLocalitiesWithPolygons_NullPolygon() {
    // Arrange
    Locality locality =
        Locality.builder().id(1L).hashtag("test").name("Test Locality").geoBoundary(null).build();

    when(localityRepository.findAll()).thenReturn(Collections.singletonList(locality));

    // Act
    List<LocalityDTO> result = localityService.getAllLocalitiesWithPolygons();

    // Assert
    assertNotNull(result);
    assertEquals(1, result.size());
    assertNull(result.get(0).getGeoBoundary());
  }

  /** Helper method to create a test polygon. */
  private Polygon createTestPolygon(double minLng, double minLat, double maxLng, double maxLat) {
    Coordinate[] coordinates =
        new Coordinate[] {
          new Coordinate(minLng, minLat),
          new Coordinate(maxLng, minLat),
          new Coordinate(maxLng, maxLat),
          new Coordinate(minLng, maxLat),
          new Coordinate(minLng, minLat) // Close
          // the
          // ring
        };
    return geometryFactory.createPolygon(coordinates);
  }
}
