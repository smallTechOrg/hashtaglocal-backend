package org.smalltech.hashtaglocal_backend.integration;

// ...existing imports...

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.Point;
import org.locationtech.jts.geom.Polygon;
import org.smalltech.hashtaglocal_backend.entity.IssueActionEntity;
import org.smalltech.hashtaglocal_backend.entity.IssueEntity;
import org.smalltech.hashtaglocal_backend.entity.Locality;
import org.smalltech.hashtaglocal_backend.entity.Location;
import org.smalltech.hashtaglocal_backend.entity.MediaEntity;
import org.smalltech.hashtaglocal_backend.entity.UserEntity;
import org.smalltech.hashtaglocal_backend.model.IssueActionApprovalStatus;
import org.smalltech.hashtaglocal_backend.model.IssueActionModel;
import org.smalltech.hashtaglocal_backend.model.IssueStatusModel;
import org.smalltech.hashtaglocal_backend.model.IssueTypeModel;
import org.smalltech.hashtaglocal_backend.model.MediaTypeModel;
import org.smalltech.hashtaglocal_backend.repository.IssueActionRepository;
import org.smalltech.hashtaglocal_backend.repository.IssueRepository;
import org.smalltech.hashtaglocal_backend.repository.LocalityRepository;
import org.smalltech.hashtaglocal_backend.repository.LocationRepository;
import org.smalltech.hashtaglocal_backend.repository.MediaRepository;
import org.smalltech.hashtaglocal_backend.repository.UserRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.transaction.annotation.Transactional;

@TestConfiguration
public class IssueTestDataConfig implements CommandLineRunner {

  private final IssueRepository issueRepository;
  private final IssueActionRepository issueActionRepository;
  private final UserRepository userRepository;
  private final LocalityRepository localityRepository;
  private final LocationRepository locationRepository;
  private final MediaRepository mediaRepository;

  public IssueTestDataConfig(
      IssueRepository issueRepository,
      IssueActionRepository issueActionRepository,
      UserRepository userRepository,
      LocalityRepository localityRepository,
      LocationRepository locationRepository,
      MediaRepository mediaRepository) {
    this.issueRepository = issueRepository;
    this.issueActionRepository = issueActionRepository;
    this.userRepository = userRepository;
    this.localityRepository = localityRepository;
    this.locationRepository = locationRepository;
    this.mediaRepository = mediaRepository;
  }

  @Override
  @Transactional
  public void run(String... args) {
    if (issueRepository.count() == 0) {
      // Create user
      UserEntity user =
          UserEntity.builder()
              .username("john_doe")
              .profilePicture("https://example.com/profile.jpg")
              .locale("en_US")
              .build();
      user = userRepository.save(user);

      GeometryFactory geometryFactory = new GeometryFactory();

      // Create or get default #world locality (ID 1) - universal fallback
      Locality defaultWorldLocality =
          localityRepository
              .findById(1L)
              .orElseGet(
                  () -> {
                    Polygon worldPolygon =
                        geometryFactory.createPolygon(
                            new Coordinate[] {
                              new Coordinate(-180, -90),
                              new Coordinate(180, -90),
                              new Coordinate(180, 90),
                              new Coordinate(-180, 90),
                              new Coordinate(-180, -90)
                            });
                    Locality newLocality =
                        Locality.builder()
                            .hashtag("world")
                            .name("World")
                            .geoBoundary(worldPolygon)
                            .build();
                    return localityRepository.save(newLocality);
                  });

      // Create Jaipur locality
      Polygon jaipurPolygon =
          geometryFactory.createPolygon(
              new Coordinate[] {
                new Coordinate(75.7, 26.8),
                new Coordinate(75.9, 26.8),
                new Coordinate(75.9, 27.0),
                new Coordinate(75.7, 27.0),
                new Coordinate(75.7, 26.8)
              });
      Locality jaipurLocality =
          Locality.builder().hashtag("Jaipur").name("Jaipur").geoBoundary(jaipurPolygon).build();
      jaipurLocality = localityRepository.save(jaipurLocality);

      // Create location with JTS Point and use default #world locality
      Point point = geometryFactory.createPoint(new Coordinate(56.78, 12.34));
      Map<String, Object> metaData = new HashMap<>();
      metaData.put("address", "Sector 3, Jawahar Nagar");
      metaData.put("colloquialName", "Near Patrika Gate");

      Location location =
          Location.builder()
              .point(point)
              .locality(defaultWorldLocality)
              .name("Sector 3, Jawahar Nagar")
              .metaData(metaData)
              .build();
      location = locationRepository.save(location);

      // Create Jaipur location
      Location jaipurLocation =
          Location.builder()
              .point(point)
              .locality(jaipurLocality)
              .name("Sector 3, Jawahar Nagar")
              .metaData(metaData)
              .build();
      jaipurLocation = locationRepository.save(jaipurLocation);

      LocalDateTime now = LocalDateTime.now();

      // Create issue 1 - older issue (world)
      IssueEntity issue1 =
          IssueEntity.builder()
              .key("JPR-001")
              .type(IssueTypeModel.POTHOLE)
              .status(IssueStatusModel.OPEN)
              .description("Large pothole causing traffic issues")
              .createdAt(now.minusDays(5))
              .updatedAt(now.minusDays(5))
              .userEntity(user)
              .location(location)
              .build();
      issue1 = issueRepository.save(issue1);

      // Create media for issue 1 (through REPORT actions)
      MediaEntity media1 =
          MediaEntity.builder()
              .type(MediaTypeModel.PHOTO)
              .url("https://sripath.com/wp-content/uploads/2025/01/iStock-174662203.jpg")
              .location(location)
              .createdAt(LocalDateTime.now())
              .build();
      media1 = mediaRepository.save(media1);
      issueActionRepository.save(
          IssueActionEntity.builder()
              .issueEntity(issue1)
              .userEntity(user)
              .action(IssueActionModel.REPORT)
              .approvalStatus(IssueActionApprovalStatus.NOT_REQUIRED)
              .media(media1)
              .createdAt(issue1.getCreatedAt())
              .build());

      MediaEntity media2 =
          MediaEntity.builder()
              .type(MediaTypeModel.PHOTO)
              .url("https://nub.news/api/image/526263/article.png")
              .location(location)
              .createdAt(LocalDateTime.now())
              .build();
      media2 = mediaRepository.save(media2);
      issueActionRepository.save(
          IssueActionEntity.builder()
              .issueEntity(issue1)
              .userEntity(user)
              .action(IssueActionModel.REPORT)
              .approvalStatus(IssueActionApprovalStatus.NOT_REQUIRED)
              .media(media2)
              .createdAt(issue1.getCreatedAt())
              .build());

      // Create issue 2 - newer issue (world)
      IssueEntity issue2 =
          IssueEntity.builder()
              .key("JPR-002")
              .type(IssueTypeModel.POTHOLE)
              .status(IssueStatusModel.OPEN)
              .description("Large pothole causing traffic issues")
              .createdAt(now.minusDays(3))
              .updatedAt(now.minusDays(3))
              .userEntity(user)
              .location(location)
              .build();
      issue2 = issueRepository.save(issue2);

      // Create media for issue 2 (through REPORT actions)
      MediaEntity media3 =
          MediaEntity.builder()
              .type(MediaTypeModel.PHOTO)
              .url("https://sripath.com/wp-content/uploads/2025/01/iStock-174662203.jpg")
              .location(location)
              .createdAt(LocalDateTime.now())
              .build();
      media3 = mediaRepository.save(media3);
      issueActionRepository.save(
          IssueActionEntity.builder()
              .issueEntity(issue2)
              .userEntity(user)
              .action(IssueActionModel.REPORT)
              .approvalStatus(IssueActionApprovalStatus.NOT_REQUIRED)
              .media(media3)
              .createdAt(issue2.getCreatedAt())
              .build());

      MediaEntity media4 =
          MediaEntity.builder()
              .type(MediaTypeModel.PHOTO)
              .url("https://nub.news/api/image/526263/article.png")
              .location(location)
              .createdAt(LocalDateTime.now())
              .build();
      media4 = mediaRepository.save(media4);
      issueActionRepository.save(
          IssueActionEntity.builder()
              .issueEntity(issue2)
              .userEntity(user)
              .action(IssueActionModel.REPORT)
              .approvalStatus(IssueActionApprovalStatus.NOT_REQUIRED)
              .media(media4)
              .createdAt(issue2.getCreatedAt())
              .build());

      // Create issue 3 - ONHOLD issue (world, newest)
      IssueEntity issue3 =
          IssueEntity.builder()
              .key("JPR-003")
              .type(IssueTypeModel.WASTE)
              .status(IssueStatusModel.ONHOLD)
              .description("Garbage pile needs attention")
              .createdAt(now.minusDays(2))
              .updatedAt(now.minusDays(2))
              .userEntity(user)
              .location(location)
              .build();
      issue3 = issueRepository.save(issue3);

      // Create media for issue 3
      MediaEntity media5 =
          MediaEntity.builder()
              .type(MediaTypeModel.PHOTO)
              .url("https://example.com/waste-photo.jpg")
              .location(location)
              .createdAt(LocalDateTime.now())
              .build();
      media5 = mediaRepository.save(media5);
      issueActionRepository.save(
          IssueActionEntity.builder()
              .issueEntity(issue3)
              .userEntity(user)
              .action(IssueActionModel.REPORT)
              .approvalStatus(IssueActionApprovalStatus.NOT_REQUIRED)
              .media(media5)
              .createdAt(issue3.getCreatedAt())
              .build());

      // Create issue 4 - Jaipur locality
      IssueEntity jaipurIssue =
          IssueEntity.builder()
              .key("JPR-004")
              .type(IssueTypeModel.POTHOLE)
              .status(IssueStatusModel.OPEN)
              .description("Jaipur pothole issue")
              .createdAt(now.minusDays(1))
              .updatedAt(now.minusDays(1))
              .userEntity(user)
              .location(jaipurLocation)
              .build();
      jaipurIssue = issueRepository.save(jaipurIssue);

      MediaEntity jaipurMedia =
          MediaEntity.builder()
              .type(MediaTypeModel.PHOTO)
              .url("https://example.com/jaipur-pothole.jpg")
              .location(jaipurLocation)
              .createdAt(LocalDateTime.now())
              .build();
      jaipurMedia = mediaRepository.save(jaipurMedia);
      issueActionRepository.save(
          IssueActionEntity.builder()
              .issueEntity(jaipurIssue)
              .userEntity(user)
              .action(IssueActionModel.REPORT)
              .approvalStatus(IssueActionApprovalStatus.NOT_REQUIRED)
              .media(jaipurMedia)
              .createdAt(jaipurIssue.getCreatedAt())
              .build());
    }
  }
}
