package org.smalltech.hashtaglocal_backend.service;

import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.smalltech.hashtaglocal_backend.entity.Location;
import org.smalltech.hashtaglocal_backend.repository.LocalityRepository;
import org.smalltech.hashtaglocal_backend.repository.LocationRepository;
import org.smalltech.hashtaglocal_backend.service.location.LocalityResolver;
import org.smalltech.hashtaglocal_backend.service.location.LocationNameExtractor;
import org.smalltech.hashtaglocal_backend.util.LocationUtil;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class LocationService {

  private final LocationRepository locationRepository;
  private final LocalityRepository localityRepository;
  private final LocalityResolver localityResolver;

  public Location save(Location location) {
    return locationRepository.save(location);
  }

  public Location createAndSaveLocation(
      Double lat, Double lng, Map<String, Object> metaData, String fallbackName) {
    if (lat == null || lng == null) {
      return null;
    }

    var defaultLocality = localityRepository.findById(1L).orElse(null);
    var locality = localityResolver.resolve(lat, lng, defaultLocality);
    var name = LocationNameExtractor.extract(metaData);

    Location location =
        Location.builder()
            .point(LocationUtil.createPoint(lat, lng))
            .name(name != null ? name : (locality != null ? locality.getName() : fallbackName))
            .locality(locality)
            .metaData(metaData)
            .build();

    return locationRepository.save(location);
  }

  /**
   * Finds all Location rows with {@code locality_id = null} and attempts to resolve the locality
   * via point-in-polygon (ST_Contains), falling back to nearest locality (ST_Distance).
   *
   * <p>Called as a second pass after geocoding, to fix up any locations that were created when the
   * localities table was empty.
   *
   * @return the number of locations that were successfully linked to a locality
   */
  public int relinkLocalities() {
    var defaultLocality = localityRepository.findById(1L).orElse(null);
    List<Location> unlinked = locationRepository.findByLocalityIsNull();
    log.info("Found {} locations with no locality — attempting to relink", unlinked.size());

    int linked = 0;
    for (Location location : unlinked) {
      double lat = location.getPoint().getY();
      double lng = location.getPoint().getX();
      var locality = localityResolver.resolve(lat, lng, defaultLocality);
      if (locality != null) {
        location.setLocality(locality);
        locationRepository.save(location);
        linked++;
        log.info("Linked location id={} to locality '{}'", location.getId(), locality.getName());
      }
    }

    return linked;
  }
}
