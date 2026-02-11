package org.smalltech.hashtaglocal_backend.repository;

import java.time.LocalDateTime;
import java.util.Optional;
import org.smalltech.hashtaglocal_backend.entity.Locality;
import org.smalltech.hashtaglocal_backend.entity.NewsImportJob;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface NewsImportJobRepository extends JpaRepository<NewsImportJob, Long> {

	@Query("SELECT n FROM NewsImportJob n WHERE n.locality = :locality " + "AND n.status = 'SUCCESS' "
			+ "ORDER BY n.importedAt DESC LIMIT 1")
	Optional<NewsImportJob> findLastSuccessfulImportForLocality(@Param("locality") Locality locality);

	@Query("SELECT n FROM NewsImportJob n WHERE n.locality = :locality " + "AND n.importedAt > :since "
			+ "AND n.status = 'SUCCESS'")
	Optional<NewsImportJob> findRecentSuccessfulImport(@Param("locality") Locality locality,
			@Param("since") LocalDateTime since);
}
