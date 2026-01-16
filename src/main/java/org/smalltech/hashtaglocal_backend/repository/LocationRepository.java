package org.smalltech.hashtaglocal_backend.repository;

import org.smalltech.hashtaglocal_backend.entity.Locality;
import org.smalltech.hashtaglocal_backend.entity.Location;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface LocationRepository extends JpaRepository<Location, Long> {
	Location findByLatAndLngAndLocality(String lat, String lng, Locality locality);
}
