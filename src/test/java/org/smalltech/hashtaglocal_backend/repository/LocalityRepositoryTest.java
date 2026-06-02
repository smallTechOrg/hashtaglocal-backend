package org.smalltech.hashtaglocal_backend.repository;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.Polygon;
import org.smalltech.hashtaglocal_backend.entity.Locality;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
public class LocalityRepositoryTest {
  @Autowired private LocalityRepository localityRepository;

  @Test
  void testSaveAndFindLocality() {
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

    Locality saved = localityRepository.save(locality);
    assertThat(saved.getId()).isNotNull();
    assertThat(saved.getHashtag()).isEqualTo("#TestCity");
    assertThat(saved.getName()).isEqualTo("Test City");
    assertThat(saved.getGeoBoundary()).isNotNull();
  }
}
