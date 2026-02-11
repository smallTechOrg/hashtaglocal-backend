package org.smalltech.hashtaglocal_backend.repository;

import java.util.List;
import org.smalltech.hashtaglocal_backend.entity.DiscoveredLocality;
import org.smalltech.hashtaglocal_backend.entity.LocalityDiscoveryRun;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface DiscoveredLocalityRepository extends JpaRepository<DiscoveredLocality, Long> {
	List<DiscoveredLocality> findByDiscoveryRun(LocalityDiscoveryRun discoveryRun);

	List<DiscoveredLocality> findByLocalityType(DiscoveredLocality.LocalityType localityType);

	List<DiscoveredLocality> findByDiscoveryRunAndLocalityType(LocalityDiscoveryRun discoveryRun,
			DiscoveredLocality.LocalityType localityType);

	List<DiscoveredLocality> findByState(String state);

	List<DiscoveredLocality> findByCountryCode(String countryCode);
}
