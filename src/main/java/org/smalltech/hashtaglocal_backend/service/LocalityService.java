package org.smalltech.hashtaglocal_backend.service;

import java.util.List;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.Polygon;
import org.smalltech.hashtaglocal_backend.dto.LocalityDTO;
import org.smalltech.hashtaglocal_backend.entity.Locality;
import org.smalltech.hashtaglocal_backend.repository.LocalityRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Service for managing locality operations.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class LocalityService {

	private final LocalityRepository localityRepository;

	/**
	 * Get all localities with their polygon boundaries. Returns data in GeoJSON
	 * format compatible with Google Maps.
	 *
	 * @return List of localities with polygon data
	 */
	@Transactional(readOnly = true)
	public List<LocalityDTO> getAllLocalitiesWithPolygons() {
		log.info("Fetching all localities with polygons");
		List<Locality> localities = localityRepository.findAll();
		log.info("Found {} localities", localities.size());

		return localities.stream().map(this::convertToDTO).collect(Collectors.toList());
	}

	/**
	 * Convert Locality entity to LocalityDTO with GeoJSON polygon.
	 */
	private LocalityDTO convertToDTO(Locality locality) {
		return LocalityDTO.builder().id(locality.getId()).hashtag(locality.getHashtag()).name(locality.getName())
				.geoBoundary(convertPolygonToGeoJSON(locality.getGeoBoundary())).build();
	}

	/**
	 * Convert JTS Polygon to GeoJSON format. GeoJSON uses [longitude, latitude]
	 * order (opposite of typical lat/lng).
	 */
	private LocalityDTO.PolygonDTO convertPolygonToGeoJSON(Polygon polygon) {
		if (polygon == null) {
			return null;
		}

		// Extract exterior ring coordinates
		Coordinate[] coordinates = polygon.getExteriorRing().getCoordinates();

		// Convert to GeoJSON format: [[[lng, lat], [lng, lat], ...]]
		double[][] ring = new double[coordinates.length][2];
		for (int i = 0; i < coordinates.length; i++) {
			ring[i][0] = coordinates[i].x; // longitude
			ring[i][1] = coordinates[i].y; // latitude
		}

		// GeoJSON Polygon structure: array of rings (first is exterior, rest are holes)
		double[][][] geoJsonCoordinates = new double[1][][];
		geoJsonCoordinates[0] = ring;

		return LocalityDTO.PolygonDTO.builder().type("Polygon").coordinates(geoJsonCoordinates).build();
	}
}
