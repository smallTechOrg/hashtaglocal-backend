package org.smalltech.hashtaglocal_backend.repository;

import org.smalltech.hashtaglocal_backend.entity.MediaEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface MediaRepository extends JpaRepository<MediaEntity, Long> {
}
