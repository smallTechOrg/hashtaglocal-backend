package org.smalltech.hashtaglocal_backend.service;

import org.smalltech.hashtaglocal_backend.entity.Locality;
import org.smalltech.hashtaglocal_backend.entity.Location;
import org.smalltech.hashtaglocal_backend.repository.LocalityRepository;
import org.smalltech.hashtaglocal_backend.repository.LocationRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class LocationService {
	@Autowired
	private LocationRepository locationRepository;
	@Autowired
	private LocalityRepository localityRepository;

	public Location addLocation(String lat, String lng, String metaData, String localityHashtag, String name) {
		Locality locality = localityRepository.findByHashtag(localityHashtag);
		if (locality == null)
			return null;
		Location location = locationRepository.findByLatAndLngAndLocality(lat, lng, locality);
		if (location == null) {
			location = new Location();
			location.setLat(lat);
			location.setLng(lng);
			location.setMetaData(metaData);
			location.setLocality(locality);
			location.setName(name);
		} else {
			location.setMetaData(metaData);
			location.setName(name);
		}
		return locationRepository.save(location);
	}
}
