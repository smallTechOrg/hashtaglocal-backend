package org.smalltech.hashtaglocal_backend.controller;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.time.LocalDateTime;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.smalltech.hashtaglocal_backend.entity.NewsImportJob;
import org.smalltech.hashtaglocal_backend.service.NewsImportService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

@ExtendWith(MockitoExtension.class)
class NewsImportControllerTest {

	@Mock
	private NewsImportService newsImportService;

	@InjectMocks
	private NewsImportController newsImportController;

	private NewsImportJob successfulJob;
	private NewsImportJob failedJob;

	@BeforeEach
	void setUp() {
		successfulJob = NewsImportJob.builder().id(1L).articlesImported(25).articlesDuplicate(5)
				.status(NewsImportJob.ImportStatus.SUCCESS).importedAt(LocalDateTime.now()).build();

		failedJob = NewsImportJob.builder().id(2L).articlesImported(0).articlesDuplicate(0)
				.status(NewsImportJob.ImportStatus.FAILED).importedAt(LocalDateTime.now()).errorMessage("API error")
				.build();
	}

	@Test
	void testImportNews_Success() {
		// Arrange
		when(newsImportService.importNewsForLocality("bengaluru")).thenReturn(successfulJob);

		// Act
		ResponseEntity<NewsImportController.ImportResponse> response = newsImportController.importNews("bengaluru");

		// Assert
		assertNotNull(response);
		assertEquals(HttpStatus.OK, response.getStatusCode());
		assertNotNull(response.getBody());
		assertTrue(response.getBody().isSuccess());
		assertEquals(1L, response.getBody().getJobId());
		assertEquals(25, response.getBody().getArticlesImported());
		assertEquals(5, response.getBody().getArticlesDuplicate());
		assertEquals("SUCCESS", response.getBody().getStatus());
		assertTrue(response.getBody().getMessage().contains("25"));
	}

	@Test
	void testImportNews_Failed() {
		// Arrange
		when(newsImportService.importNewsForLocality("bengaluru")).thenReturn(failedJob);

		// Act
		ResponseEntity<NewsImportController.ImportResponse> response = newsImportController.importNews("bengaluru");

		// Assert
		assertNotNull(response);
		assertEquals(HttpStatus.OK, response.getStatusCode());
		assertNotNull(response.getBody());
		assertFalse(response.getBody().isSuccess());
		assertEquals("FAILED", response.getBody().getStatus());
	}

	@Test
	void testImportNews_ServiceException() {
		// Arrange
		when(newsImportService.importNewsForLocality(anyString())).thenThrow(new RuntimeException("Service error"));

		// Act & Assert
		assertThrows(RuntimeException.class, () -> newsImportController.importNews("bengaluru"));
	}
}
