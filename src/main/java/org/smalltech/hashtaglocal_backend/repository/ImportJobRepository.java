package org.smalltech.hashtaglocal_backend.repository;

import java.util.List;
import org.smalltech.hashtaglocal_backend.entity.ImportJob;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ImportJobRepository extends JpaRepository<ImportJob, Long> {
	List<ImportJob> findAllByOrderByStartedAtDesc();

	List<ImportJob> findByStatus(ImportJob.ImportJobStatus status);
}
