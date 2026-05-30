package org.smalltech.hashtaglocal_backend.repository;

import java.util.Optional;
import org.smalltech.hashtaglocal_backend.entity.LinkCache;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface LinkCacheRepository extends JpaRepository<LinkCache, Long> {
  Optional<LinkCache> findByCanonicalUrl(String canonicalUrl);
}
