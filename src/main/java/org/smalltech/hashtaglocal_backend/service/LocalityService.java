package org.smalltech.hashtaglocal_backend.service;

import java.util.List;
import org.smalltech.hashtaglocal_backend.entity.Locality;
import org.smalltech.hashtaglocal_backend.repository.LocalityRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class LocalityService {
	@Autowired
	private LocalityRepository localityRepository;

	// This is a stub. In a real implementation, you would use a spatial query.
	public Locality getLocality(String lat, String lng) {
		// TODO: Implement polygon contains logic for geoBoundary
		List<Locality> all = localityRepository.findAll();
		// Placeholder: return first locality (for now)
		return all.isEmpty() ? null : all.get(0);
	}
}
