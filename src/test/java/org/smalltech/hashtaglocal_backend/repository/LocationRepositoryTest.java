package org.smalltech.hashtaglocal_backend.repository;

import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.Polygon;
import org.smalltech.hashtaglocal_backend.entity.Locality;
import org.smalltech.hashtaglocal_backend.entity.Location;
import org.smalltech.hashtaglocal_backend.util.LocationUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
public class LocationRepositoryTest {
  @Autowired private LocationRepository locationRepository;

  @Autowired private LocalityRepository localityRepository;

  @Test
  public void testSaveAndFindLocation() {
    GeometryFactory geometryFactory = new GeometryFactory();
    Coordinate[] coordinates =
        new Coordinate[] {
          new Coordinate(12.9716, 77.5946),
          new Coordinate(12.9717, 77.5947),
          new Coordinate(12.9718, 77.5948),
          new Coordinate(12.9716, 77.5946) // close the polygon
        };
    Polygon polygon = geometryFactory.createPolygon(coordinates);

    Locality locality =
        Locality.builder().hashtag("#TestCity").geoBoundary(polygon).name("Test City").build();
    localityRepository.save(locality);

    Location location = new Location();
    location.setPoint(LocationUtil.createPoint(12.34, 56.78));
    location.setName("Test Location");
    Map<String, Object> metaData = new HashMap<>();
    metaData.put("foo", "bar");
    location.setMetaData(metaData);
    location.setLocality(locality);
    Location saved = locationRepository.save(location);

    Location found = locationRepository.findById(saved.getId()).orElse(null);
    Assertions.assertNotNull(found);
    Assertions.assertEquals("Test Location", found.getName());
    Assertions.assertNotNull(found.getMetaData());
    Assertions.assertEquals("bar", found.getMetaData().get("foo"));
    Assertions.assertEquals(locality.getId(), found.getLocality().getId());
  }
}
