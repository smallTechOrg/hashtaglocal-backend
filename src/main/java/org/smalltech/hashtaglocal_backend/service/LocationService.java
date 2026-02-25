package org.smalltech.hashtaglocal_backend.service;

import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.smalltech.hashtaglocal_backend.entity.Location;
import org.smalltech.hashtaglocal_backend.repository.LocalityRepository;
import org.smalltech.hashtaglocal_backend.repository.LocationRepository;
import org.smalltech.hashtaglocal_backend.service.location.LocalityResolver;
import org.smalltech.hashtaglocal_backend.service.location.LocationNameExtractor;
import org.smalltech.hashtaglocal_backend.util.LocationUtil;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class LocationService {

  private final LocationRepository locationRepository;
  private final LocalityRepository localityRepository;
  private final LocalityResolver localityResolver;

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
            .name(name != null ? name : locality.getName())
            .locality(locality)
            .metaData(metaData)
            .build();

    return locationRepository.save(location);
  }
}
