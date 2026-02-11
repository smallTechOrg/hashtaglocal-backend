package org.smalltech.hashtaglocal_backend.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import java.util.stream.Collectors;
import org.smalltech.hashtaglocal_backend.dto.NewsArticleDTO;
import org.smalltech.hashtaglocal_backend.dto.NewsResponse;
import org.smalltech.hashtaglocal_backend.entity.Locality;
import org.smalltech.hashtaglocal_backend.entity.NewsArticleEntity;
import org.smalltech.hashtaglocal_backend.model.IssueTypeModel;
import org.smalltech.hashtaglocal_backend.repository.LocalityRepository;
import org.smalltech.hashtaglocal_backend.repository.NewsArticleRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

/**
 * Controller for news article API
 */
@RestController
@RequestMapping("/api/news")
@Tag(name = "News", description = "News articles API")
public class NewsController {

	private final NewsArticleRepository newsArticleRepository;
	private final LocalityRepository localityRepository;

	public NewsController(NewsArticleRepository newsArticleRepository, LocalityRepository localityRepository) {
		this.newsArticleRepository = newsArticleRepository;
		this.localityRepository = localityRepository;
	}

	@GetMapping("/{hashtag}")
	@Operation(summary = "Get news articles for a locality", description = "Returns paginated news articles for the specified locality with optional category filter")
	@ApiResponse(responseCode = "200", description = "Successful response", content = @Content(mediaType = "application/json", schema = @Schema(implementation = NewsResponse.class)))
	@ApiResponse(responseCode = "404", description = "Locality not found")
	public ResponseEntity<NewsResponse> getNews(
			@PathVariable @Parameter(description = "Locality hashtag") String hashtag,
			@RequestParam(required = false, defaultValue = "ALL") @Parameter(description = "Filter by category. Allowed values: ALL, POTHOLE, WASTE, FOOTPATH, POLLUTION, HYGIENE, SAFETY") String category,
			@RequestParam(required = false, defaultValue = "1") @Parameter(description = "Page number (1-indexed)") int page,
			@RequestParam(required = false, defaultValue = "10") @Parameter(description = "Number of articles per page (recommended: 6-20)") int pageSize) {

		// Find locality
		Locality locality = localityRepository.findByHashtag(hashtag)
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Locality not found: " + hashtag));

		// Parse category filter
		IssueTypeModel categoryFilter = null;
		if (!"ALL".equalsIgnoreCase(category)) {
			try {
				categoryFilter = IssueTypeModel.valueOf(category.toUpperCase());
			} catch (IllegalArgumentException e) {
				throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid category: " + category);
			}
		}

		// Validate pagination parameters
		if (page < 1) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Page must be >= 1");
		}
		if (pageSize < 1 || pageSize > 100) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Page size must be between 1 and 100");
		}

		// Fetch articles
		Pageable pageable = PageRequest.of(page - 1, pageSize); // Convert to 0-indexed
		Page<NewsArticleEntity> articlesPage = newsArticleRepository.findByLocalityAndCategory(locality, categoryFilter,
				pageable);

		// Get total count
		long totalResults = newsArticleRepository.countByLocalityAndCategory(locality, categoryFilter);

		// Convert to DTOs
		List<NewsArticleDTO> articleDTOs = articlesPage.getContent().stream().map(this::convertToDTO)
				.collect(Collectors.toList());

		// Build response
		NewsResponse response = NewsResponse.builder().status("success").totalResults(totalResults).page(page)
				.pageSize(pageSize).articles(articleDTOs).build();

		return ResponseEntity.ok(response);
	}

	private NewsArticleDTO convertToDTO(NewsArticleEntity entity) {
		return NewsArticleDTO.builder().id(String.valueOf(entity.getId())).title(entity.getTitle())
				.description(entity.getDescription()).content(entity.getContent()).category(entity.getCategory().name())
				.source(NewsArticleDTO.SourceDTO.builder().id(entity.getSourceId()).name(entity.getSourceName())
						.build())
				.author(entity.getAuthor()).url(entity.getUrl()).urlToImage(entity.getUrlToImage())
				.publishedAt(entity.getPublishedAt())
				.location(entity.getLocality() != null ? entity.getLocality().getName() : null).build();
	}
}
