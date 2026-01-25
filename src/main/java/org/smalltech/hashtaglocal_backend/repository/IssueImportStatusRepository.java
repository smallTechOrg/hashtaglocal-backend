package org.smalltech.hashtaglocal_backend.repository;

import java.util.Optional;
import org.smalltech.hashtaglocal_backend.entity.IssueImportStatus;
import org.smalltech.hashtaglocal_backend.model.IssueImportSource;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface IssueImportStatusRepository extends JpaRepository<IssueImportStatus, Long> {
	boolean existsBySourceAndSourceIssueId(IssueImportSource source, String sourceIssueId);

	Optional<IssueImportStatus> findBySourceAndSourceIssueId(IssueImportSource source, String sourceIssueId);

	java.util.List<IssueImportStatus> findByJobId(Long jobId);
}
