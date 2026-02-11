package org.smalltech.hashtaglocal_backend.repository;

import static org.junit.jupiter.api.Assertions.*;

import java.time.LocalDateTime;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.Polygon;
import org.smalltech.hashtaglocal_backend.entity.Locality;
import org.smalltech.hashtaglocal_backend.entity.NewsArticleEntity;
import org.smalltech.hashtaglocal_backend.model.IssueTypeModel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class NewsArticleRepositoryTest {

	@Autowired
	private TestEntityManager entityManager;

	@Autowired
	private NewsArticleRepository newsArticleRepository;

	private Locality testLocality;

	@BeforeEach
	void setUp() {
		testLocality = new Locality();
		testLocality.setHashtag("bengaluru");
		testLocality.setName("Bengaluru");
		testLocality.setGeoBoundary(createTestPolygon());
		testLocality = entityManager.persistAndFlush(testLocality);
	}

	@Test
	void testFindByExternalId() {
		// Arrange
		NewsArticleEntity article = createTestArticle("ext-123", "Test Article", IssueTypeModel.POTHOLE);
		entityManager.persistAndFlush(article);

		// Act
		Optional<NewsArticleEntity> found = newsArticleRepository.findByExternalId("ext-123");

		// Assert
		assertTrue(found.isPresent());
		assertEquals("Test Article", found.get().getTitle());
	}

	@Test
	void testFindByLocalityAndCategory_AllCategories() {
		// Arrange
		createTestArticle("art-1", "Pothole Article", IssueTypeModel.POTHOLE);
		createTestArticle("art-2", "Waste Article", IssueTypeModel.WASTE);
		createTestArticle("art-3", "Safety Article", IssueTypeModel.SAFETY);
		entityManager.flush();

		// Act
		Page<NewsArticleEntity> result = newsArticleRepository.findByLocalityAndCategory(testLocality, null,
				PageRequest.of(0, 10));

		// Assert
		assertEquals(3, result.getTotalElements());
	}

	@Test
	void testFindByLocalityAndCategory_SpecificCategory() {
		// Arrange
		createTestArticle("art-1", "Pothole Article 1", IssueTypeModel.POTHOLE);
		createTestArticle("art-2", "Pothole Article 2", IssueTypeModel.POTHOLE);
		createTestArticle("art-3", "Waste Article", IssueTypeModel.WASTE);
		entityManager.flush();

		// Act
		Page<NewsArticleEntity> result = newsArticleRepository.findByLocalityAndCategory(testLocality,
				IssueTypeModel.POTHOLE, PageRequest.of(0, 10));

		// Assert
		assertEquals(2, result.getTotalElements());
		assertTrue(result.getContent().stream().allMatch(a -> a.getCategory() == IssueTypeModel.POTHOLE));
	}

	@Test
	void testCountByLocalityAndCategory() {
		// Arrange
		createTestArticle("art-1", "Pothole Article", IssueTypeModel.POTHOLE);
		createTestArticle("art-2", "Waste Article", IssueTypeModel.WASTE);
		entityManager.flush();

		// Act
		long totalCount = newsArticleRepository.countByLocalityAndCategory(testLocality, null);
		long potholeCount = newsArticleRepository.countByLocalityAndCategory(testLocality, IssueTypeModel.POTHOLE);

		// Assert
		assertEquals(2, totalCount);
		assertEquals(1, potholeCount);
	}

	@Test
	void testFindByLocalityAndCategory_Pagination() {
		// Arrange
		for (int i = 1; i <= 25; i++) {
			createTestArticle("art-" + i, "Article " + i, IssueTypeModel.POTHOLE);
		}
		entityManager.flush();

		// Act
		Page<NewsArticleEntity> page1 = newsArticleRepository.findByLocalityAndCategory(testLocality,
				IssueTypeModel.POTHOLE, PageRequest.of(0, 10));
		Page<NewsArticleEntity> page2 = newsArticleRepository.findByLocalityAndCategory(testLocality,
				IssueTypeModel.POTHOLE, PageRequest.of(1, 10));

		// Assert
		assertEquals(25, page1.getTotalElements());
		assertEquals(10, page1.getContent().size());
		assertEquals(10, page2.getContent().size());
		assertNotEquals(page1.getContent().get(0).getId(), page2.getContent().get(0).getId());
	}

	private NewsArticleEntity createTestArticle(String externalId, String title, IssueTypeModel category) {
		NewsArticleEntity article = NewsArticleEntity.builder().externalId(externalId).title(title)
				.description("Test description").content("Test content").category(category).sourceId("test-source")
				.sourceName("Test Source").author("Test Author").url("https://test.com/" + externalId)
				.urlToImage("https://test.com/image.jpg").publishedAt(LocalDateTime.now()).locality(testLocality)
				.build();
		return entityManager.persist(article);
	}

	private Polygon createTestPolygon() {
		GeometryFactory geometryFactory = new GeometryFactory();
		Coordinate[] coordinates = new Coordinate[]{new Coordinate(77.5, 12.9), new Coordinate(77.6, 12.9),
				new Coordinate(77.6, 13.0), new Coordinate(77.5, 13.0), new Coordinate(77.5, 12.9)};
		return geometryFactory.createPolygon(coordinates);
	}
}
