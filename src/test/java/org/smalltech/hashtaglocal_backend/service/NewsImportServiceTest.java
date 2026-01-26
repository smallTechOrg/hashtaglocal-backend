package org.smalltech.hashtaglocal_backend.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.util.Arrays;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.smalltech.hashtaglocal_backend.dto.newsapi.NewsApiRequest;
import org.smalltech.hashtaglocal_backend.dto.newsapi.NewsApiResponse;
import org.smalltech.hashtaglocal_backend.entity.Locality;
import org.smalltech.hashtaglocal_backend.entity.NewsArticleEntity;
import org.smalltech.hashtaglocal_backend.entity.NewsImportJob;
import org.smalltech.hashtaglocal_backend.repository.LocalityRepository;
import org.smalltech.hashtaglocal_backend.repository.NewsArticleRepository;
import org.smalltech.hashtaglocal_backend.repository.NewsImportJobRepository;

@ExtendWith(MockitoExtension.class)
class NewsImportServiceTest {

	@Mock
	private NewsApiService newsApiService;

	@Mock
	private NewsArticleRepository newsArticleRepository;

	@Mock
	private NewsImportJobRepository newsImportJobRepository;

	@Mock
	private LocalityRepository localityRepository;

	@InjectMocks
	private NewsImportService newsImportService;

	private Locality testLocality;
	private NewsApiResponse mockApiResponse;

	@BeforeEach
	void setUp() {
		testLocality = new Locality();
		testLocality.setId(1L);
		testLocality.setHashtag("bengaluru");
		testLocality.setName("Bengaluru");

		// Setup mock API response
		mockApiResponse = new NewsApiResponse();
		NewsApiResponse.ArticlesResult articlesResult = new NewsApiResponse.ArticlesResult();
		articlesResult.setCount(2);
		articlesResult.setPages(1);
		articlesResult.setPage(1);

		NewsApiResponse.NewsApiArticle article1 = new NewsApiResponse.NewsApiArticle();
		article1.setUri("article-1");
		article1.setTitle("Pothole on MG Road");
		article1.setBody("A large pothole has been reported on MG Road. This is causing issues.");
		article1.setUrl("https://example.com/article1");
		article1.setImageUrl("https://example.com/image1.jpg");
		article1.setDateTime("2026-01-25T10:00:00");

		NewsApiResponse.NewsApiArticle.NewsApiSource source1 = new NewsApiResponse.NewsApiArticle.NewsApiSource();
		source1.setUri("the-hindu");
		source1.setTitle("The Hindu");
		article1.setSource(source1);

		NewsApiResponse.NewsApiArticle.NewsApiAuthor author1 = new NewsApiResponse.NewsApiArticle.NewsApiAuthor();
		author1.setName("John Doe");
		article1.setAuthors(Arrays.asList(author1));

		NewsApiResponse.NewsApiArticle article2 = new NewsApiResponse.NewsApiArticle();
		article2.setUri("article-2");
		article2.setTitle("Waste Management Problem");
		article2.setBody("Waste collection has been delayed in several areas.");
		article2.setUrl("https://example.com/article2");
		article2.setDateTime("2026-01-24T15:30:00");

		NewsApiResponse.NewsApiArticle.NewsApiSource source2 = new NewsApiResponse.NewsApiArticle.NewsApiSource();
		source2.setUri("deccan-herald");
		source2.setTitle("Deccan Herald");
		article2.setSource(source2);

		articlesResult.setResults(Arrays.asList(article1, article2));
		mockApiResponse.setArticles(articlesResult);
	}

	@Test
	void testImportNewsForLocality_Success() {
		// Arrange
		when(localityRepository.findByHashtag("bengaluru")).thenReturn(Optional.of(testLocality));
		when(newsApiService.searchArticles(any(NewsApiRequest.class))).thenReturn(mockApiResponse);
		when(newsArticleRepository.findByExternalId(anyString())).thenReturn(Optional.empty());
		when(newsArticleRepository.save(any(NewsArticleEntity.class)))
				.thenAnswer(invocation -> invocation.getArgument(0));
		when(newsImportJobRepository.save(any(NewsImportJob.class)))
				.thenAnswer(invocation -> invocation.getArgument(0));

		// Act
		NewsImportJob result = newsImportService.importNewsForLocality("bengaluru");

		// Assert
		assertNotNull(result);
		assertEquals(NewsImportJob.ImportStatus.SUCCESS, result.getStatus());
		assertTrue(result.getArticlesImported() > 0);
		assertEquals(0, result.getArticlesDuplicate());
		verify(newsArticleRepository, atLeastOnce()).save(any(NewsArticleEntity.class));
	}

	@Test
	void testImportNewsForLocality_LocalityNotFound() {
		// Arrange
		when(localityRepository.findByHashtag("unknown")).thenReturn(Optional.empty());

		// Act & Assert
		assertThrows(RuntimeException.class, () -> newsImportService.importNewsForLocality("unknown"));
	}

	@Test
	void testImportNewsForLocality_HandlesDuplicates() {
		// Arrange
		when(localityRepository.findByHashtag("bengaluru")).thenReturn(Optional.of(testLocality));
		when(newsApiService.searchArticles(any(NewsApiRequest.class))).thenReturn(mockApiResponse);

		// First article is duplicate, second is new
		when(newsArticleRepository.findByExternalId("article-1"))
				.thenReturn(Optional.of(new NewsArticleEntity()));
		when(newsArticleRepository.findByExternalId("article-2")).thenReturn(Optional.empty());
		when(newsArticleRepository.save(any(NewsArticleEntity.class)))
				.thenAnswer(invocation -> invocation.getArgument(0));
		when(newsImportJobRepository.save(any(NewsImportJob.class)))
				.thenAnswer(invocation -> invocation.getArgument(0));

		// Act
		NewsImportJob result = newsImportService.importNewsForLocality("bengaluru");

		// Assert
		assertNotNull(result);
		assertTrue(result.getArticlesDuplicate() > 0);
	}

	@Test
	void testImportNewsForLocality_HandlesApiError() {
		// Arrange
		when(localityRepository.findByHashtag("bengaluru")).thenReturn(Optional.of(testLocality));
		// Make all API calls fail
		when(newsApiService.searchArticles(any(NewsApiRequest.class)))
				.thenThrow(new RuntimeException("API Error"));
		when(newsImportJobRepository.save(any(NewsImportJob.class)))
				.thenAnswer(invocation -> invocation.getArgument(0));

		// Act
		NewsImportJob result = newsImportService.importNewsForLocality("bengaluru");

		// Assert
		assertNotNull(result);
		// The service catches API errors per keyword and continues
		// So it will succeed with 0 articles imported
		assertEquals(NewsImportJob.ImportStatus.SUCCESS, result.getStatus());
		assertEquals(0, result.getArticlesImported());
	}
}
