package org.smalltech.hashtaglocal_backend.repository;

import java.util.List;
import org.smalltech.hashtaglocal_backend.entity.EventSourceEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface EventSourceRepository extends JpaRepository<EventSourceEntity, Long> {

  List<EventSourceEntity> findAllByOrderByIdAsc();

  List<EventSourceEntity> findAllByEnabledTrueOrderByIdAsc();

  boolean existsBySiteIdAndAction(String siteId, String action);

  boolean existsBySiteIdAndActionAndIdNot(String siteId, String action, Long id);
}
