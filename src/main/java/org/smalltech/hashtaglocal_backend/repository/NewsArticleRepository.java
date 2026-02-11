package org.smalltech.hashtaglocal_backend.repository;

import java.util.Optional;
import org.smalltech.hashtaglocal_backend.entity.Locality;
import org.smalltech.hashtaglocal_backend.entity.NewsArticleEntity;
import org.smalltech.hashtaglocal_backend.model.IssueTypeModel;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface NewsArticleRepository extends JpaRepository<NewsArticleEntity, Long> {

	Optional<NewsArticleEntity> findByExternalId(String externalId);

	// Find by locality with optional category filter
	@Query("SELECT n FROM NewsArticleEntity n WHERE n.locality = :locality "
			+ "AND (:category IS NULL OR n.category = :category) " + "ORDER BY n.publishedAt DESC")
	Page<NewsArticleEntity> findByLocalityAndCategory(@Param("locality") Locality locality,
			@Param("category") IssueTypeModel category, Pageable pageable);

	// Count articles by locality and category
	@Query("SELECT COUNT(n) FROM NewsArticleEntity n WHERE n.locality = :locality "
			+ "AND (:category IS NULL OR n.category = :category)")
	long countByLocalityAndCategory(@Param("locality") Locality locality, @Param("category") IssueTypeModel category);
}
