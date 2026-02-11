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
		var name = getNameFromMetaData(metaData);

		Location location = Location.builder().point(LocationUtil.createPoint(lat, lng))
				.name(name != null ? name : locality.getName()).locality(locality).metaData(metaData).build();

		return locationRepository.save(location);
	}

	private Locality resolveLocality(Double latitude, Double longitude, Locality defaultLocality) {
		if (latitude == null || longitude == null) {
			return defaultLocality;
		}

		return localityRepository.findContainingLocality(latitude, longitude)
				.or(() -> localityRepository.findNearestLocality(latitude, longitude)).orElse(defaultLocality);
	}

	
	String getNameFromMetaData(Map<String, Object> metaData) {
		if (metaData == null) {
			return null;
		}

		String formattedAddress = getStringValue(metaData, "formatted_address");
		if (formattedAddress == null || formattedAddress.isEmpty()) {
			return null;
		}

		String region = getStringValue(metaData, "region");
		String postalCode = getStringValue(metaData, "postal_code");
		String[] parts = formattedAddress.split(",\\s*");

		StringBuilder result = new StringBuilder();
		for (String part : parts) {
			String trimmed = part.trim();
			if (region != null && trimmed.contains(region)) {
				continue;
			}
			if (postalCode != null && trimmed.contains(postalCode)) {
				continue;
			}
			if (result.length() > 0) {
				result.append(" - ");
			}
			result.append(trimmed);
		}

		return result.length() > 0 ? result.toString() : null;
	}

	private String getStringValue(Map<String, Object> map, String key) {
		Object value = map.get(key);
		return value instanceof String s ? s : null;
	}
}
