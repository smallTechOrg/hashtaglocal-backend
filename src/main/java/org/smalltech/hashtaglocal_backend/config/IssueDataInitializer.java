package org.smalltech.hashtaglocal_backend.config;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.Point;
import org.locationtech.jts.geom.Polygon;
import org.smalltech.hashtaglocal_backend.entity.IssueEntity;
import org.smalltech.hashtaglocal_backend.entity.Locality;
import org.smalltech.hashtaglocal_backend.entity.Location;
import org.smalltech.hashtaglocal_backend.entity.UserEntity;
import org.smalltech.hashtaglocal_backend.model.IssueStatusModel;
import org.smalltech.hashtaglocal_backend.model.IssueTypeModel;
import org.smalltech.hashtaglocal_backend.repository.IssueRepository;
import org.smalltech.hashtaglocal_backend.repository.LocalityRepository;
import org.smalltech.hashtaglocal_backend.repository.LocationRepository;
import org.smalltech.hashtaglocal_backend.repository.UserRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.transaction.annotation.Transactional;

@Configuration
@RequiredArgsConstructor
@Profile("!test")
public class IssueDataInitializer implements CommandLineRunner {

	private final IssueRepository issueRepository;
	private final UserRepository userRepository;
	private final LocalityRepository localityRepository;
	private final LocationRepository locationRepository;

	@Override
	@Transactional
	public void run(String... args) {
		// Create or get admin user (ID 1)
		UserEntity adminUser = userRepository.findById(1L).orElseGet(() -> {
			UserEntity user = UserEntity.builder().username("Admin").profilePicture("https://example.com/admin.jpg")
					.locale("en").build();
			return userRepository.save(user);
		});

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

		// Create default location if it doesn't exist, with default #world locality
		Location location = locationRepository.findById(1L).orElseGet(() -> {
			GeometryFactory geometryFactory = new GeometryFactory();
			Point point = geometryFactory.createPoint(new Coordinate(56.78, 12.34));
			Map<String, Object> metaData = new HashMap<>();
			metaData.put("address", "Central Jaipur");
			metaData.put("colloquialName", "City Center");

			Location newLocation = Location.builder().point(point).locality(defaultWorldLocality).name("Central Jaipur")
					.metaData(metaData).build();
			return locationRepository.save(newLocation);
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

		// Only insert issue if none exist
		if (issueRepository.count() == 0) {
			IssueEntity issue = IssueEntity.builder().key("JPR-001").type(IssueTypeModel.POTHOLE)
					.status(IssueStatusModel.OPEN).description("Large pothole causing traffic issues")
					.createdAt(LocalDateTime.parse("2025-12-26T18:00:00"))
					.updatedAt(LocalDateTime.parse("2025-12-26T18:00:00")).userEntity(adminUser).location(location)
					.build();

			issueRepository.save(issue);
		}
	}
}
