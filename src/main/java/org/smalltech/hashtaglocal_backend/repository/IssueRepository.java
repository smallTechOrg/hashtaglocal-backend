package org.smalltech.hashtaglocal_backend.repository;

import java.util.List;
import java.util.Optional;
import org.smalltech.hashtaglocal_backend.entity.IssueEntity;
import org.smalltech.hashtaglocal_backend.model.IssueStatusModel;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface IssueRepository extends JpaRepository<IssueEntity, Long> {
	@EntityGraph(attributePaths = {"userEntity", "location", "location.locality"})
	Optional<IssueEntity> findById(Long id);

	@EntityGraph(attributePaths = {"userEntity", "location", "location.locality"})
	List<IssueEntity> findAll();

	@EntityGraph(attributePaths = {"userEntity", "location", "location.locality"})
	List<IssueEntity> findByStatus(IssueStatusModel status);

	@EntityGraph(attributePaths = {"userEntity", "location", "location.locality"})
	List<IssueEntity> findByStatusOrderByCreatedAtDesc(IssueStatusModel status);

	@EntityGraph(attributePaths = {"userEntity", "location", "location.locality"})
	List<IssueEntity> findByStatusInOrderByCreatedAtDesc(List<IssueStatusModel> statuses);
}
