package org.smalltech.hashtaglocal_backend.repository;

import java.util.List;
import org.smalltech.hashtaglocal_backend.entity.ImportJob;
import org.smalltech.hashtaglocal_backend.entity.LocalityImportStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface LocalityImportStatusRepository extends JpaRepository<LocalityImportStatus, Long> {
	List<LocalityImportStatus> findByImportJob(ImportJob importJob);

	List<LocalityImportStatus> findByImportStatus(LocalityImportStatus.ImportStatus importStatus);

	List<LocalityImportStatus> findByImportJobAndImportStatus(ImportJob importJob,
			LocalityImportStatus.ImportStatus importStatus);
}
