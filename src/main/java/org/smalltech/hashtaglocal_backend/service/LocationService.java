package org.smalltech.hashtaglocal_backend.service;

import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.smalltech.hashtaglocal_backend.entity.Locality;
import org.smalltech.hashtaglocal_backend.entity.Location;
import org.smalltech.hashtaglocal_backend.repository.LocalityRepository;
import org.smalltech.hashtaglocal_backend.repository.LocationRepository;
import org.smalltech.hashtaglocal_backend.util.LocationUtil;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class LocationService {

	private final LocationRepository locationRepository;
	private final LocalityRepository localityRepository;

	public Location createAndSaveLocation(Double lat, Double lng, Map<String, Object> metaData, String fallbackName) {
		if (lat == null || lng == null) {
			return null;
		}

		var defaultLocality = localityRepository.findById(1L).orElse(null);
		var locality = resolveLocality(lat, lng, defaultLocality);

		Location location = Location.builder().point(LocationUtil.createPoint(lat, lng))
				.name(locality != null ? locality.getName() : fallbackName).locality(locality).metaData(metaData)
				.build();

		return locationRepository.save(location);
	}

	private Locality resolveLocality(Double latitude, Double longitude, Locality defaultLocality) {
		if (latitude == null || longitude == null) {
			return defaultLocality;
		}

		return localityRepository.findContainingLocality(latitude, longitude)
				.or(() -> localityRepository.findNearestLocality(latitude, longitude)).orElse(defaultLocality);
	}
}
