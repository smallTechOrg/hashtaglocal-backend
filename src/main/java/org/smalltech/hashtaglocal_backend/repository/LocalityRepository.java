package org.smalltech.hashtaglocal_backend.repository;

import java.util.Optional;
import org.smalltech.hashtaglocal_backend.entity.Locality;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface LocalityRepository extends JpaRepository<Locality, Long> {
  Optional<Locality> findByHashtag(String hashtag);

  /**
   * Resolve a hashtag tolerant of the leading '#'. Hashtags are stored with the '#' prefix (e.g.
   * "#gangtok") but URLs/clients commonly send the bare word ("gangtok"). Matches either form.
   */
  @Query(
      "SELECT l FROM Locality l WHERE l.hashtag = :tag OR l.hashtag = CONCAT('#', :tag) "
          + "OR CONCAT('#', l.hashtag) = :tag")
  Optional<Locality> findByHashtagFlexible(@Param("tag") String tag);

  @Query(
      value =
          "SELECT * FROM localities l WHERE ST_Contains(l.geo_boundary, ST_SetSRID(ST_MakePoint(:lng, :lat), ST_SRID(l.geo_boundary))) ORDER BY ST_Area(l.geo_boundary) ASC LIMIT 1",
      nativeQuery = true)
  Optional<Locality> findContainingLocality(
      @Param("lat") double latitude, @Param("lng") double longitude);

  @Query(
      value =
          "SELECT * FROM localities l ORDER BY ST_Distance(l.geo_boundary::geography, ST_SetSRID(ST_MakePoint(:lng, :lat), ST_SRID(l.geo_boundary))::geography) ASC LIMIT 1",
      nativeQuery = true)
  Optional<Locality> findNearestLocality(
      @Param("lat") double latitude, @Param("lng") double longitude);
}
