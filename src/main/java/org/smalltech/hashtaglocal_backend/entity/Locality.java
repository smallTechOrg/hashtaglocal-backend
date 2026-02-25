package org.smalltech.hashtaglocal_backend.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.locationtech.jts.geom.Polygon;

@Entity
@Table(name = "localities")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Locality {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(unique = true, nullable = false)
  private String hashtag;

  @Column(nullable = false, columnDefinition = "geometry(Polygon,4326)")
  private Polygon geoBoundary;

  @Column(nullable = false)
  private String name;

  // parent not required in v1
}
