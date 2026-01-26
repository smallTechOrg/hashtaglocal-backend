package org.smalltech.hashtaglocal_backend.controller;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.smalltech.hashtaglocal_backend.dto.NewsResponse;
import org.smalltech.hashtaglocal_backend.entity.Locality;
import org.smalltech.hashtaglocal_backend.entity.NewsArticleEntity;
import org.smalltech.hashtaglocal_backend.model.IssueTypeModel;
import org.smalltech.hashtaglocal_backend.repository.LocalityRepository;
import org.smalltech.hashtaglocal_backend.repository.NewsArticleRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.server.ResponseStatusException;

@ExtendWith(MockitoExtension.class)
class NewsControllerTest {

	@Mock
	private NewsArticleRepository newsArticleRepository;

	@Mock
	private LocalityRepository localityRepository;

	@InjectMocks
	private NewsController newsController;

	private Locality testLocality;
	private NewsArticleEntity testArticle1;
	private NewsArticleEntity testArticle2;

	@BeforeEach
	void setUp() {
		testLocality = new Locality();
		testLocality.setId(1L);
		testLocality.setHashtag("bengaluru");
		testLocality.setName("Bengaluru");

		testArticle1 = NewsArticleEntity.builder().id(1L).externalId("article-1").title("Pothole problem on MG Road")
				.description("Multiple potholes reported").content("Full content").category(IssueTypeModel.POTHOLE)
				.sourceId("the-hindu").sourceName("The Hindu").author("John Doe").url("https://example.com/article1")
				.urlToImage("https://example.com/image1.jpg").publishedAt(LocalDateTime.now()).locality(testLocality)
				.build();

		testArticle2 = NewsArticleEntity.builder().id(2L).externalId("article-2").title("Waste management issues")
				.description("Garbage collection delayed").content("Full content 2").category(IssueTypeModel.WASTE)
				.sourceId("deccan-herald").sourceName("Deccan Herald").author("Jane Smith")
				.url("https://example.com/article2").urlToImage("https://example.com/image2.jpg")
				.publishedAt(LocalDateTime.now().minusDays(1)).locality(testLocality).build();
	}

	@Test
	void testGetNews_Success() {
		// Arrange
		List<NewsArticleEntity> articles = Arrays.asList(testArticle1, testArticle2);
		Page<NewsArticleEntity> page = new PageImpl<>(articles, PageRequest.of(0, 10), 2);

		when(localityRepository.findByHashtag("bengaluru")).thenReturn(Optional.of(testLocality));
		when(newsArticleRepository.findByLocalityAndCategory(eq(testLocality), isNull(), any(Pageable.class)))
				.thenReturn(page);
		when(newsArticleRepository.countByLocalityAndCategory(eq(testLocality), isNull())).thenReturn(2L);

		// Act
		ResponseEntity<NewsResponse> response = newsController.getNews("bengaluru", "ALL", 1, 10);

		// Assert
		assertNotNull(response);
		assertEquals(HttpStatus.OK, response.getStatusCode());
		assertNotNull(response.getBody());
		assertEquals("success", response.getBody().getStatus());
		assertEquals(2, response.getBody().getTotalResults());
		assertEquals(1, response.getBody().getPage());
		assertEquals(10, response.getBody().getPageSize());
		assertEquals(2, response.getBody().getArticles().size());

		// Verify first article
		var article1DTO = response.getBody().getArticles().get(0);
		assertEquals("1", article1DTO.getId());
		assertEquals("Pothole problem on MG Road", article1DTO.getTitle());
		assertEquals("POTHOLE", article1DTO.getCategory());
		assertEquals("The Hindu", article1DTO.getSource().getName());
	}

	@Test
	void testGetNews_WithCategoryFilter() {
		// Arrange
		List<NewsArticleEntity> articles = Arrays.asList(testArticle1);
		Page<NewsArticleEntity> page = new PageImpl<>(articles, PageRequest.of(0, 10), 1);

		when(localityRepository.findByHashtag("bengaluru")).thenReturn(Optional.of(testLocality));
		when(newsArticleRepository.findByLocalityAndCategory(eq(testLocality), eq(IssueTypeModel.POTHOLE),
				any(Pageable.class))).thenReturn(page);
		when(newsArticleRepository.countByLocalityAndCategory(eq(testLocality), eq(IssueTypeModel.POTHOLE)))
				.thenReturn(1L);

		// Act
		ResponseEntity<NewsResponse> response = newsController.getNews("bengaluru", "POTHOLE", 1, 10);

		// Assert
		assertNotNull(response);
		assertEquals(HttpStatus.OK, response.getStatusCode());
		assertEquals(1, response.getBody().getTotalResults());
		assertEquals(1, response.getBody().getArticles().size());
		assertEquals("POTHOLE", response.getBody().getArticles().get(0).getCategory());
	}

	@Test
	void testGetNews_LocalityNotFound() {
		// Arrange
		when(localityRepository.findByHashtag("unknown")).thenReturn(Optional.empty());

		// Act & Assert
		assertThrows(ResponseStatusException.class, () -> newsController.getNews("unknown", "ALL", 1, 10));
	}

	@Test
	void testGetNews_InvalidCategory() {
		// Arrange
		when(localityRepository.findByHashtag("bengaluru")).thenReturn(Optional.of(testLocality));

		// Act & Assert
		assertThrows(ResponseStatusException.class,
				() -> newsController.getNews("bengaluru", "INVALID_CATEGORY", 1, 10));
	}

	@Test
	void testGetNews_InvalidPageNumber() {
		// Arrange
		when(localityRepository.findByHashtag("bengaluru")).thenReturn(Optional.of(testLocality));

		// Act & Assert
		assertThrows(ResponseStatusException.class, () -> newsController.getNews("bengaluru", "ALL", 0, 10));
	}

	@Test
	void testGetNews_InvalidPageSize() {
		// Arrange
		when(localityRepository.findByHashtag("bengaluru")).thenReturn(Optional.of(testLocality));

		// Act & Assert
		assertThrows(ResponseStatusException.class, () -> newsController.getNews("bengaluru", "ALL", 1, 101));
		assertThrows(ResponseStatusException.class, () -> newsController.getNews("bengaluru", "ALL", 1, 0));
	}

	@Test
	void testGetNews_Pagination() {
		// Arrange
		List<NewsArticleEntity> articles = Arrays.asList(testArticle1);
		Page<NewsArticleEntity> page = new PageImpl<>(articles, PageRequest.of(1, 5), 10);

		when(localityRepository.findByHashtag("bengaluru")).thenReturn(Optional.of(testLocality));
		when(newsArticleRepository.findByLocalityAndCategory(eq(testLocality), isNull(), any(Pageable.class)))
				.thenReturn(page);
		when(newsArticleRepository.countByLocalityAndCategory(eq(testLocality), isNull())).thenReturn(10L);

		// Act
		ResponseEntity<NewsResponse> response = newsController.getNews("bengaluru", "ALL", 2, 5);

		// Assert
		assertNotNull(response);
		assertEquals(HttpStatus.OK, response.getStatusCode());
		assertEquals(10, response.getBody().getTotalResults());
		assertEquals(2, response.getBody().getPage());
		assertEquals(5, response.getBody().getPageSize());
	}

	@Test
	void testGetNews_EmptyResults() {
		// Arrange
		Page<NewsArticleEntity> emptyPage = new PageImpl<>(Arrays.asList(), PageRequest.of(0, 10), 0);

		when(localityRepository.findByHashtag("bengaluru")).thenReturn(Optional.of(testLocality));
		when(newsArticleRepository.findByLocalityAndCategory(eq(testLocality), isNull(), any(Pageable.class)))
				.thenReturn(emptyPage);
		when(newsArticleRepository.countByLocalityAndCategory(eq(testLocality), isNull())).thenReturn(0L);

		// Act
		ResponseEntity<NewsResponse> response = newsController.getNews("bengaluru", "ALL", 1, 10);

		// Assert
		assertNotNull(response);
		assertEquals(HttpStatus.OK, response.getStatusCode());
		assertEquals(0, response.getBody().getTotalResults());
		assertEquals(0, response.getBody().getArticles().size());
	}
}
