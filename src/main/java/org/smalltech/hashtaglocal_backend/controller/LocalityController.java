package org.smalltech.hashtaglocal_backend.controller;

import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.smalltech.hashtaglocal_backend.dto.LocalityDTO;
import org.smalltech.hashtaglocal_backend.service.LocalityService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Public API endpoints for locality data. Used by client applications to render
 * localities on maps.
 */
@RestController
@RequestMapping("/api/localities")
@RequiredArgsConstructor
@Slf4j
public class LocalityController {

	private final LocalityService localityService;

	/**
	 * Get all localities with their polygon boundaries. Returns GeoJSON format
	 * compatible with Google Maps API.
	 *
	 * Example response: [ { "id": 1, "hashtag": "indiranagar", "name":
	 * "Indiranagar", "geoBoundary": { "type": "Polygon", "coordinates": [[[lng,
	 * lat], [lng, lat], ...]] } } ]
	 *
	 * @return List of localities with polygon data
	 */
	@GetMapping("/polygons")
	public ResponseEntity<List<LocalityDTO>> getAllLocalitiesWithPolygons() {
		log.info("Received request to get all localities with polygons");
		List<LocalityDTO> localities = localityService.getAllLocalitiesWithPolygons();
		log.info("Returning {} localities with polygons", localities.size());
		return ResponseEntity.ok(localities);
	}
}
