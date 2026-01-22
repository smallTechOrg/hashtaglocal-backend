package org.smalltech.hashtaglocal_backend.repository;

import java.util.List;
import java.util.Optional;
import org.smalltech.hashtaglocal_backend.entity.CityImportStatus;
import org.smalltech.hashtaglocal_backend.entity.ImportJob;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface CityImportStatusRepository extends JpaRepository<CityImportStatus, Long> {
	List<CityImportStatus> findByImportJob(ImportJob importJob);

	List<CityImportStatus> findByStatus(CityImportStatus.CityImportResult status);

	List<CityImportStatus> findByStatusIn(List<CityImportStatus.CityImportResult> statuses);

	Optional<CityImportStatus> findByCityNameAndStatus(String cityName, CityImportStatus.CityImportResult status);

	List<CityImportStatus> findByCityName(String cityName);

	Long countByImportJobAndStatus(ImportJob importJob, CityImportStatus.CityImportResult status);
}
