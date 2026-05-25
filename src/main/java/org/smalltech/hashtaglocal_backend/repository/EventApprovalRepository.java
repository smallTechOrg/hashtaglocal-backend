package org.smalltech.hashtaglocal_backend.repository;

import java.util.List;
import org.smalltech.hashtaglocal_backend.entity.EventApprovalEntity;
import org.smalltech.hashtaglocal_backend.model.EventApprovalStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface EventApprovalRepository extends JpaRepository<EventApprovalEntity, Long> {

  List<EventApprovalEntity> findByStatus(EventApprovalStatus status);

  List<EventApprovalEntity> findByStatusIn(List<EventApprovalStatus> statuses);
}
