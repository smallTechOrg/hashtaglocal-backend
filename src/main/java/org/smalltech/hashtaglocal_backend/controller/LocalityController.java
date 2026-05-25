package org.smalltech.hashtaglocal_backend.controller;

import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.smalltech.hashtaglocal_backend.dto.LocalityDTO;
import org.smalltech.hashtaglocal_backend.model.NewAPIResponse;
import org.smalltech.hashtaglocal_backend.model.response.LocalityHashtagResponseData;
import org.smalltech.hashtaglocal_backend.service.LocalityService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Public API endpoints for locality data. Used by client applications to render localities on maps.
 */
@RestController
@RequestMapping("/api/localities")
@RequiredArgsConstructor
@Slf4j
public class LocalityController {

  private final LocalityService localityService;

  /**
   * Get all localities with their polygon boundaries. Returns GeoJSON format compatible with Google
   * Maps API.
   *
   * <p>Example response: [ { "id": 1, "hashtag": "indiranagar", "name": "Indiranagar",
   * "geoBoundary": { "type": "Polygon", "coordinates": [[[lng, lat], [lng, lat], ...]] } } ]
   *
   * @return List of localities with polygon data
   */
  @GetMapping("/hashtag")
  public ResponseEntity<NewAPIResponse<LocalityHashtagResponseData>> getHashtagByCoordinates(
      @RequestParam double lat, @RequestParam double lng) {
    log.info("Resolving hashtag for lat={} lng={}", lat, lng);
    return localityService
        .resolveByCoordinates(lat, lng)
        .map(
            l ->
                ResponseEntity.ok(
                    NewAPIResponse.<LocalityHashtagResponseData>builder()
                        .data(LocalityHashtagResponseData.builder().hashtag(l.getHashtag()).build())
                        .build()))
        .orElse(ResponseEntity.notFound().build());
  }

  @GetMapping("/polygons")
  public ResponseEntity<List<LocalityDTO>> getAllLocalitiesWithPolygons() {
    log.info("Received request to get all localities with polygons");
    List<LocalityDTO> localities = localityService.getAllLocalitiesWithPolygons();
    log.info("Returning {} localities with polygons", localities.size());
    return ResponseEntity.ok(localities);
  }
}
