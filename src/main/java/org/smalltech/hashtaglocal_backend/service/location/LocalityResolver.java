package org.smalltech.hashtaglocal_backend.service.location;

import lombok.RequiredArgsConstructor;
import org.smalltech.hashtaglocal_backend.entity.Locality;
import org.smalltech.hashtaglocal_backend.repository.LocalityRepository;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class LocalityResolver {

  private final LocalityRepository localityRepository;

  public Locality resolve(Double latitude, Double longitude, Locality defaultLocality) {
    if (latitude == null || longitude == null) {
      return defaultLocality;
    }

    return localityRepository
        .findContainingLocality(latitude, longitude)
        .or(() -> localityRepository.findNearestLocality(latitude, longitude))
        .orElse(defaultLocality);
  }
}
