package org.smalltech.hashtaglocal_backend.repository;

import java.util.List;
import org.smalltech.hashtaglocal_backend.entity.LocalityDiscoveryRun;
import org.smalltech.hashtaglocal_backend.entity.RawLocalityDiscovery;
import org.smalltech.hashtaglocal_backend.entity.RawLocalityDiscovery.DiscoverySource;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface RawLocalityDiscoveryRepository extends JpaRepository<RawLocalityDiscovery, Long> {
	List<RawLocalityDiscovery> findByDiscoveryRun(LocalityDiscoveryRun discoveryRun);

	List<RawLocalityDiscovery> findBySource(DiscoverySource source);

	List<RawLocalityDiscovery> findByLocalityType(RawLocalityDiscovery.LocalityType localityType);

	List<RawLocalityDiscovery> findByState(String state);
}
