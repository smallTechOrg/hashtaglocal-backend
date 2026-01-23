package org.smalltech.hashtaglocal_backend.integration;

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
import org.smalltech.hashtaglocal_backend.entity.MediaEntity;
import org.smalltech.hashtaglocal_backend.entity.UserEntity;
import org.smalltech.hashtaglocal_backend.model.IssueStatusModel;
import org.smalltech.hashtaglocal_backend.model.IssueTypeModel;
import org.smalltech.hashtaglocal_backend.model.MediaTypeModel;
import org.smalltech.hashtaglocal_backend.repository.IssueRepository;
import org.smalltech.hashtaglocal_backend.repository.LocalityRepository;
import org.smalltech.hashtaglocal_backend.repository.LocationRepository;
import org.smalltech.hashtaglocal_backend.repository.MediaRepository;
import org.smalltech.hashtaglocal_backend.repository.UserRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.test.context.TestConfiguration;

@TestConfiguration
@RequiredArgsConstructor
public class IssueTestDataConfig implements CommandLineRunner {

	private final IssueRepository issueRepository;
	private final UserRepository userRepository;
	private final LocalityRepository localityRepository;
	private final LocationRepository locationRepository;
	private final MediaRepository mediaRepository;

	@Override
	public void run(String... args) {
		// Only insert if table is empty
		if (issueRepository.count() == 0) {
			// Create user
			UserEntity user = UserEntity.builder().username("john_doe")
					.profilePicture("https://example.com/profile.jpg").locale("en_US").build();
			user = userRepository.save(user);

			// Create locality with JTS Polygon
			GeometryFactory geometryFactory = new GeometryFactory();
			Polygon polygon = geometryFactory
					.createPolygon(new Coordinate[]{new Coordinate(75.7, 26.8), new Coordinate(75.9, 26.8),
							new Coordinate(75.9, 27.0), new Coordinate(75.7, 27.0), new Coordinate(75.7, 26.8)});

			Locality locality = Locality.builder().hashtag("Jaipur").name("Jaipur").geoBoundary(polygon).build();
			locality = localityRepository.save(locality);

			// Create location with JTS Point
			Point point = geometryFactory.createPoint(new Coordinate(56.78, 12.34));
			Map<String, Object> metaData = new HashMap<>();
			metaData.put("address", "Sector 3, Jawahar Nagar");
			metaData.put("colloquialName", "Near Patrika Gate");

			Location location = Location.builder().point(point).locality(locality).name("Sector 3, Jawahar Nagar")
					.metaData(metaData).build();
			location = locationRepository.save(location);

			// Create issue 1
			IssueEntity issue1 = IssueEntity.builder().key("JPR-001").type(IssueTypeModel.POTHOLE)
					.status(IssueStatusModel.OPEN).description("Large pothole causing traffic issues")
					.createdAt(LocalDateTime.parse("2025-12-26T18:00:00"))
					.updatedAt(LocalDateTime.parse("2025-12-26T18:00:00")).userEntity(user).location(location).build();
			issue1 = issueRepository.save(issue1);

			// Create media for issue 1
			MediaEntity media1 = MediaEntity.builder().issue(issue1).type(MediaTypeModel.PHOTO)
					.url("https://sripath.com/wp-content/uploads/2025/01/iStock-174662203.jpg").location(location)
					.build();
			mediaRepository.save(media1);

			MediaEntity media2 = MediaEntity.builder().issue(issue1).type(MediaTypeModel.PHOTO)
					.url("https://nub.news/api/image/526263/article.png").location(location).build();
			mediaRepository.save(media2);

			// Create issue 2
			IssueEntity issue2 = IssueEntity.builder().key("JPR-002").type(IssueTypeModel.POTHOLE)
					.status(IssueStatusModel.OPEN).description("Large pothole causing traffic issues")
					.createdAt(LocalDateTime.parse("2025-12-26T18:00:00"))
					.updatedAt(LocalDateTime.parse("2025-12-26T18:00:00")).userEntity(user).location(location).build();
			issue2 = issueRepository.save(issue2);

			// Create media for issue 2
			MediaEntity media3 = MediaEntity.builder().issue(issue2).type(MediaTypeModel.PHOTO)
					.url("https://sripath.com/wp-content/uploads/2025/01/iStock-174662203.jpg").location(location)
					.build();
			mediaRepository.save(media3);

			MediaEntity media4 = MediaEntity.builder().issue(issue2).type(MediaTypeModel.PHOTO)
					.url("https://nub.news/api/image/526263/article.png").location(location).build();
			mediaRepository.save(media4);
		}
	}
}
