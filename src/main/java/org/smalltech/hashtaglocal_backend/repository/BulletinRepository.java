package org.smalltech.hashtaglocal_backend.repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.smalltech.hashtaglocal_backend.entity.BulletinEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface BulletinRepository extends JpaRepository<BulletinEntity, Long> {

  Optional<BulletinEntity> findByLocalityIdAndDate(Long localityId, LocalDate date);

  List<BulletinEntity> findByDateOrderByLocalityId(LocalDate date);

  List<BulletinEntity> findByLocalityIdOrderByDateDesc(Long localityId);

  Optional<BulletinEntity> findByQuizId(Long quizId);

  @Query(
      value =
          "SELECT b.summary->>'text' FROM bulletins b"
              + " WHERE b.locality_id = :localityId"
              + " AND b.summary IS NOT NULL AND b.summary->>'text' IS NOT NULL"
              + " ORDER BY b.date DESC LIMIT :limit",
      nativeQuery = true)
  List<String> findRecentWeatherSummaries(
      @Param("localityId") Long localityId, @Param("limit") int limit);
}
