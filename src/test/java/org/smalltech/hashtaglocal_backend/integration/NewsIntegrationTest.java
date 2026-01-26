package org.smalltech.hashtaglocal_backend.integration;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.time.LocalDateTime;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.Polygon;
import org.smalltech.hashtaglocal_backend.entity.Locality;
import org.smalltech.hashtaglocal_backend.entity.NewsArticleEntity;
import org.smalltech.hashtaglocal_backend.model.IssueTypeModel;
import org.smalltech.hashtaglocal_backend.repository.LocalityRepository;
import org.smalltech.hashtaglocal_backend.repository.NewsArticleRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class NewsIntegrationTest {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private NewsArticleRepository newsArticleRepository;

	@Autowired
	private LocalityRepository localityRepository;

	private Locality testLocality;

	@BeforeEach
	void setUp() {
		// Clean up
		newsArticleRepository.deleteAll();
		localityRepository.deleteAll();

		// Create test locality
		testLocality = new Locality();
		testLocality.setHashtag("bengaluru");
		testLocality.setName("Bengaluru");
		testLocality.setGeoBoundary(createTestPolygon());
		testLocality = localityRepository.save(testLocality);

		// Create test articles
		createTestArticle("ext-1", "Pothole on MG Road", IssueTypeModel.POTHOLE);
		createTestArticle("ext-2", "Waste management issue", IssueTypeModel.WASTE);
		createTestArticle("ext-3", "Footpath damage", IssueTypeModel.FOOTPATH);
		createTestArticle("ext-4", "Another pothole", IssueTypeModel.POTHOLE);
		createTestArticle("ext-5", "Pollution levels high", IssueTypeModel.POLLUTION);
	}

	@Test
	void testGetNews_AllCategories() throws Exception {
		mockMvc.perform(get("/api/news/bengaluru").param("category", "ALL").param("page", "1").param("pageSize", "10"))
				.andExpect(status().isOk()).andExpect(jsonPath("$.status").value("success"))
				.andExpect(jsonPath("$.total_results").value(5)).andExpect(jsonPath("$.page").value(1))
				.andExpect(jsonPath("$.page_size").value(10)).andExpect(jsonPath("$.articles").isArray())
				.andExpect(jsonPath("$.articles.length()").value(5));
	}

	@Test
	void testGetNews_FilterByCategory() throws Exception {
		mockMvc.perform(
				get("/api/news/bengaluru").param("category", "POTHOLE").param("page", "1").param("pageSize", "10"))
				.andExpect(status().isOk()).andExpect(jsonPath("$.status").value("success"))
				.andExpect(jsonPath("$.total_results").value(2)).andExpect(jsonPath("$.articles").isArray())
				.andExpect(jsonPath("$.articles.length()").value(2))
				.andExpect(jsonPath("$.articles[0].category").value("POTHOLE"))
				.andExpect(jsonPath("$.articles[1].category").value("POTHOLE"));
	}

	@Test
	void testGetNews_Pagination() throws Exception {
		// Create more articles
		for (int i = 6; i <= 15; i++) {
			createTestArticle("ext-" + i, "Article " + i, IssueTypeModel.WASTE);
		}

		mockMvc.perform(get("/api/news/bengaluru").param("category", "ALL").param("page", "1").param("pageSize", "5"))
				.andExpect(status().isOk()).andExpect(jsonPath("$.total_results").value(15))
				.andExpect(jsonPath("$.page").value(1)).andExpect(jsonPath("$.page_size").value(5))
				.andExpect(jsonPath("$.articles.length()").value(5));

		mockMvc.perform(get("/api/news/bengaluru").param("category", "ALL").param("page", "2").param("pageSize", "5"))
				.andExpect(status().isOk()).andExpect(jsonPath("$.page").value(2))
				.andExpect(jsonPath("$.articles.length()").value(5));
	}

	@Test
	void testGetNews_LocalityNotFound() throws Exception {
		mockMvc.perform(get("/api/news/unknown")).andExpect(status().isNotFound());
	}

	@Test
	void testGetNews_InvalidCategory() throws Exception {
		mockMvc.perform(get("/api/news/bengaluru").param("category", "INVALID")).andExpect(status().isBadRequest());
	}

	@Test
	void testGetNews_InvalidPagination() throws Exception {
		// Invalid page number
		mockMvc.perform(get("/api/news/bengaluru").param("page", "0")).andExpect(status().isBadRequest());

		// Invalid page size
		mockMvc.perform(get("/api/news/bengaluru").param("pageSize", "0")).andExpect(status().isBadRequest());
		mockMvc.perform(get("/api/news/bengaluru").param("pageSize", "101")).andExpect(status().isBadRequest());
	}

	@Test
	void testGetNews_DefaultParameters() throws Exception {
		mockMvc.perform(get("/api/news/bengaluru")).andExpect(status().isOk()).andExpect(jsonPath("$.page").value(1))
				.andExpect(jsonPath("$.page_size").value(10)).andExpect(jsonPath("$.total_results").value(5));
	}

	@Test
	void testGetNews_ArticleStructure() throws Exception {
		mockMvc.perform(
				get("/api/news/bengaluru").param("category", "POTHOLE").param("page", "1").param("pageSize", "1"))
				.andExpect(status().isOk()).andExpect(jsonPath("$.articles[0].id").exists())
				.andExpect(jsonPath("$.articles[0].title").exists())
				.andExpect(jsonPath("$.articles[0].description").exists())
				.andExpect(jsonPath("$.articles[0].content").exists())
				.andExpect(jsonPath("$.articles[0].category").value("POTHOLE"))
				.andExpect(jsonPath("$.articles[0].source").exists())
				.andExpect(jsonPath("$.articles[0].source.name").value("Test Source"))
				.andExpect(jsonPath("$.articles[0].author").value("Test Author"))
				.andExpect(jsonPath("$.articles[0].url").exists())
				.andExpect(jsonPath("$.articles[0].published_at").exists())
				.andExpect(jsonPath("$.articles[0].location").value("Bengaluru"));
	}

	@Test
	void testGetNews_EmptyResults() throws Exception {
		// Create new locality with no articles
		Locality emptyLocality = new Locality();
		emptyLocality.setHashtag("mumbai");
		emptyLocality.setName("Mumbai");
		emptyLocality.setGeoBoundary(createTestPolygon());
		localityRepository.save(emptyLocality);

		mockMvc.perform(get("/api/news/mumbai")).andExpect(status().isOk())
				.andExpect(jsonPath("$.total_results").value(0)).andExpect(jsonPath("$.articles").isEmpty());
	}

	private void createTestArticle(String externalId, String title, IssueTypeModel category) {
		NewsArticleEntity article = NewsArticleEntity.builder().externalId(externalId).title(title)
				.description("Test description for " + title).content("Full content of " + title).category(category)
				.sourceId("test-source").sourceName("Test Source").author("Test Author")
				.url("https://test.com/" + externalId).urlToImage("https://test.com/image.jpg")
				.publishedAt(LocalDateTime.now()).locality(testLocality).build();
		newsArticleRepository.save(article);
	}

	private Polygon createTestPolygon() {
		GeometryFactory geometryFactory = new GeometryFactory();
		Coordinate[] coordinates = new Coordinate[]{new Coordinate(77.5, 12.9), new Coordinate(77.6, 12.9),
				new Coordinate(77.6, 13.0), new Coordinate(77.5, 13.0), new Coordinate(77.5, 12.9)};
		return geometryFactory.createPolygon(coordinates);
	}
}
