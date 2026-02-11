package org.smalltech.hashtaglocal_backend.controller;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.smalltech.hashtaglocal_backend.dto.LocalityDTO;
import org.smalltech.hashtaglocal_backend.service.LocalityService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

/**
 * Unit tests for LocalityController.
 */
@DisplayName("LocalityController Tests")
class LocalityControllerTest {

	@Mock
	private LocalityService localityService;

	@InjectMocks
	private LocalityController localityController;

	@BeforeEach
	void setup() {
		MockitoAnnotations.openMocks(this);
	}

	@Test
	@DisplayName("Should return all localities with polygons successfully")
	void testGetAllLocalitiesWithPolygons_Success() {
		// Arrange
		LocalityDTO.PolygonDTO polygon1 = LocalityDTO.PolygonDTO.builder().type("Polygon").coordinates(new double[][][]{
				{{77.6408, 12.9716}, {77.6500, 12.9716}, {77.6500, 12.9800}, {77.6408, 12.9800}, {77.6408, 12.9716}}})
				.build();

		LocalityDTO.PolygonDTO polygon2 = LocalityDTO.PolygonDTO.builder().type("Polygon").coordinates(new double[][][]{
				{{77.6100, 12.9352}, {77.6200, 12.9352}, {77.6200, 12.9400}, {77.6100, 12.9400}, {77.6100, 12.9352}}})
				.build();

		LocalityDTO locality1 = LocalityDTO.builder().id(1L).hashtag("indiranagar").name("Indiranagar")
				.geoBoundary(polygon1).build();

		LocalityDTO locality2 = LocalityDTO.builder().id(2L).hashtag("koramangala").name("Koramangala")
				.geoBoundary(polygon2).build();

		List<LocalityDTO> expectedLocalities = Arrays.asList(locality1, locality2);
		when(localityService.getAllLocalitiesWithPolygons()).thenReturn(expectedLocalities);

		// Act
		ResponseEntity<List<LocalityDTO>> response = localityController.getAllLocalitiesWithPolygons();

		// Assert
		assertNotNull(response);
		assertEquals(HttpStatus.OK, response.getStatusCode());
		assertNotNull(response.getBody());
		assertEquals(2, response.getBody().size());

		LocalityDTO firstLocality = response.getBody().get(0);
		assertEquals(1L, firstLocality.getId());
		assertEquals("indiranagar", firstLocality.getHashtag());
		assertEquals("Indiranagar", firstLocality.getName());
		assertNotNull(firstLocality.getGeoBoundary());
		assertEquals("Polygon", firstLocality.getGeoBoundary().getType());
		assertNotNull(firstLocality.getGeoBoundary().getCoordinates());

		verify(localityService, times(1)).getAllLocalitiesWithPolygons();
	}

	@Test
	@DisplayName("Should return empty list when no localities exist")
	void testGetAllLocalitiesWithPolygons_EmptyList() {
		// Arrange
		when(localityService.getAllLocalitiesWithPolygons()).thenReturn(Collections.emptyList());

		// Act
		ResponseEntity<List<LocalityDTO>> response = localityController.getAllLocalitiesWithPolygons();

		// Assert
		assertNotNull(response);
		assertEquals(HttpStatus.OK, response.getStatusCode());
		assertNotNull(response.getBody());
		assertTrue(response.getBody().isEmpty());

		verify(localityService, times(1)).getAllLocalitiesWithPolygons();
	}

	@Test
	@DisplayName("Should verify service is called correctly")
	void testGetAllLocalitiesWithPolygons_ServiceInvocation() {
		// Arrange
		List<LocalityDTO> mockLocalities = Collections.emptyList();
		when(localityService.getAllLocalitiesWithPolygons()).thenReturn(mockLocalities);

		// Act
		localityController.getAllLocalitiesWithPolygons();

		// Assert
		verify(localityService, times(1)).getAllLocalitiesWithPolygons();
		verifyNoMoreInteractions(localityService);
	}
}
