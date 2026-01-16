package org.smalltech.hashtaglocal_backend.repository;

import org.smalltech.hashtaglocal_backend.entity.Locality;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface LocalityRepository extends JpaRepository<Locality, Long> {
	Locality findByHashtag(String hashtag);
}
