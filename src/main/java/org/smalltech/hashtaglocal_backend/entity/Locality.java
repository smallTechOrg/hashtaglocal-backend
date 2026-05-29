package org.smalltech.hashtaglocal_backend.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.ToString;
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

  /**
   * Boundary polygon for point-in-polygon resolution. Nullable for root/virtual localities (e.g.
   * {@code #india}) that have no single polygon and exist only as a parent for aggregation.
   */
  @Column(columnDefinition = "geometry(Polygon,4326)")
  private Polygon geoBoundary;

  @Column(nullable = false)
  private String name;

  /**
   * Parent locality in the hashtag tree. {@code null} for the root ({@code #india}); every other
   * locality points at the root. Used to aggregate child feeds under a parent channel.
   *
   * <p>Excluded from Lombok {@code toString}/{@code equals} to avoid recursion on the
   * self-relation.
   */
  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "parent_id")
  @ToString.Exclude
  @EqualsAndHashCode.Exclude
  private Locality parent;
}
