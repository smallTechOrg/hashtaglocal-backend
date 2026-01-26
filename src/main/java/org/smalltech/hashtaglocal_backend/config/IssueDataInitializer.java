package org.smalltech.hashtaglocal_backend.config;

import lombok.RequiredArgsConstructor;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.Polygon;
import org.smalltech.hashtaglocal_backend.entity.Locality;
import org.smalltech.hashtaglocal_backend.repository.LocalityRepository;
import org.smalltech.hashtaglocal_backend.repository.LocationRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.transaction.annotation.Transactional;

@Configuration
@RequiredArgsConstructor
@Profile("!test")
public class IssueDataInitializer implements CommandLineRunner {

	private final LocalityRepository localityRepository;
	private final LocationRepository locationRepository;

	@Override
	@Transactional
	public void run(String... args) {
		// Create or get default #world locality (ID 1) - universal fallback
		Locality defaultWorldLocality = localityRepository.findById(1L).orElseGet(() -> {
			GeometryFactory geometryFactory = new GeometryFactory();
			// World-wide polygon (covering major parts of Earth)
			Polygon worldPolygon = geometryFactory
					.createPolygon(new Coordinate[]{new Coordinate(-180, -90), new Coordinate(180, -90),
							new Coordinate(180, 90), new Coordinate(-180, 90), new Coordinate(-180, -90)});
			Locality newLocality = Locality.builder().hashtag("world").name("World").geoBoundary(worldPolygon).build();
			return localityRepository.save(newLocality);
		});

		// Ensure all existing locations have a locality assigned (use default #world if
		// null)
		// This handles any legacy data
		locationRepository.findAll().forEach(loc -> {
			if (loc.getLocality() == null) {
				loc.setLocality(defaultWorldLocality);
				locationRepository.save(loc);
			}
		});
	}
}
