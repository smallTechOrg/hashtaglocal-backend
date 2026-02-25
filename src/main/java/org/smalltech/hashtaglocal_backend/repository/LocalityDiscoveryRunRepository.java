package org.smalltech.hashtaglocal_backend.repository;

import java.util.List;
import org.smalltech.hashtaglocal_backend.entity.LocalityDiscoveryRun;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface LocalityDiscoveryRunRepository extends JpaRepository<LocalityDiscoveryRun, Long> {
  List<LocalityDiscoveryRun> findByCountryCode(String countryCode);

  List<LocalityDiscoveryRun> findByStatus(LocalityDiscoveryRun.DiscoveryStatus status);
}
