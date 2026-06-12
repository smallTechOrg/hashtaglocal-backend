package org.smalltech.hashtaglocal_backend.repository;

import java.time.LocalDate;
import java.util.Optional;
import org.smalltech.hashtaglocal_backend.entity.PeriodicDataEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface PeriodicDataRepository extends JpaRepository<PeriodicDataEntity, Long> {

  Optional<PeriodicDataEntity> findByLocalityIdAndDateAndDataType(
      Long localityId, LocalDate date, String dataType);
}
