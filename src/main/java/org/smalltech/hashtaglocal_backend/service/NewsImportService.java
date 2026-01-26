package org.smalltech.hashtaglocal_backend.service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.smalltech.hashtaglocal_backend.dto.newsapi.NewsApiRequest;
import org.smalltech.hashtaglocal_backend.dto.newsapi.NewsApiResponse;
import org.smalltech.hashtaglocal_backend.entity.Locality;
import org.smalltech.hashtaglocal_backend.entity.NewsArticleEntity;
import org.smalltech.hashtaglocal_backend.entity.NewsImportJob;
import org.smalltech.hashtaglocal_backend.model.IssueTypeModel;
import org.smalltech.hashtaglocal_backend.repository.LocalityRepository;
import org.smalltech.hashtaglocal_backend.repository.NewsArticleRepository;
import org.smalltech.hashtaglocal_backend.repository.NewsImportJobRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Service to import news articles from NewsAPI.ai
 */
@Service
public class NewsImportService {

	private static final Logger logger = LoggerFactory.getLogger(NewsImportService.class);

	private final NewsApiService newsApiService;
	private final NewsArticleRepository newsArticleRepository;
	private final NewsImportJobRepository newsImportJobRepository;
	private final LocalityRepository localityRepository;

	// Keywords for categorizing news articles
	private static final String[] POTHOLE_KEYWORDS = {"pothole", "road damage", "crater", "road defect"};
	private static final String[] WASTE_KEYWORDS = {"garbage", "waste", "trash", "litter", "dump"};
	private static final String[] FOOTPATH_KEYWORDS = {"footpath", "sidewalk", "pavement", "pedestrian path"};
	private static final String[] POLLUTION_KEYWORDS = {"pollution", "air quality", "smog", "emissions"};
	private static final String[] HYGIENE_KEYWORDS = {"hygiene", "sanitation", "cleanliness", "sewage"};
	private static final String[] SAFETY_KEYWORDS = {"safety", "crime", "accident", "danger", "hazard"};

	public NewsImportService(NewsApiService newsApiService, NewsArticleRepository newsArticleRepository,
			NewsImportJobRepository newsImportJobRepository, LocalityRepository localityRepository) {
		this.newsApiService = newsApiService;
		this.newsArticleRepository = newsArticleRepository;
		this.newsImportJobRepository = newsImportJobRepository;
		this.localityRepository = localityRepository;
	}

	/**
	 * Import news for a specific locality (hashtag)
	 */
	@Transactional
	public NewsImportJob importNewsForLocality(String hashtag) {
		logger.info("Starting news import for hashtag: {}", hashtag);

		// Find locality
		Locality locality = localityRepository.findByHashtag(hashtag)
				.orElseThrow(() -> new RuntimeException("Locality not found: " + hashtag));

		// Check if we already imported recently (within last 24 hours)
		LocalDateTime yesterday = LocalDateTime.now().minusHours(24);
		var recentImport = newsImportJobRepository.findRecentSuccessfulImport(locality, yesterday);
		if (recentImport.isPresent()) {
			logger.info("News already imported recently for {}, skipping", hashtag);
			return recentImport.get();
		}

		NewsImportJob job = NewsImportJob.builder().locality(locality).importedAt(LocalDateTime.now())
				.articlesImported(0).articlesDuplicate(0).status(NewsImportJob.ImportStatus.SUCCESS).build();

		try {
			int totalImported = 0;
			int totalDuplicates = 0;

			// Import news for each category
			for (IssueTypeModel category : IssueTypeModel.values()) {
				if (category == IssueTypeModel.OTHER) {
					continue; // Skip OTHER category
				}

				var result = importNewsForCategory(locality, category);
				totalImported += result.imported;
				totalDuplicates += result.duplicates;
			}

			job.setArticlesImported(totalImported);
			job.setArticlesDuplicate(totalDuplicates);
			logger.info("News import completed for {}. Imported: {}, Duplicates: {}", hashtag, totalImported,
					totalDuplicates);

		} catch (Exception e) {
			logger.error("Error importing news for {}: {}", hashtag, e.getMessage(), e);
			job.setStatus(NewsImportJob.ImportStatus.FAILED);
			job.setErrorMessage(e.getMessage());
		}

		return newsImportJobRepository.save(job);
	}

	private ImportResult importNewsForCategory(Locality locality, IssueTypeModel category) {
		logger.info("Importing news for {} - category: {}", locality.getHashtag(), category);

		String[] keywords = getCategoryKeywords(category);
		int imported = 0;
		int duplicates = 0;

		for (String keyword : keywords) {
			try {
				// Build query combining location and keyword
				String query = String.format("%s %s", locality.getName(), keyword);

				NewsApiRequest request = NewsApiRequest.builder().query(query)
						// Use last 30 days
						.dateStart(NewsApiService.formatDate(LocalDate.now().minusDays(30)))
						.dateEnd(NewsApiService.formatDate(LocalDate.now())).lang("eng") // English articles
						.articlesPage(1).articlesCount(20) // Limit per keyword
						.sortBy("date").sortByAsc("desc").build();

				NewsApiResponse response = newsApiService.searchArticles(request);

				if (response.getArticles() != null && response.getArticles().getResults() != null) {
					for (NewsApiResponse.NewsApiArticle apiArticle : response.getArticles().getResults()) {
						try {
							// Check if article already exists
							if (newsArticleRepository.findByExternalId(apiArticle.getUri()).isPresent()) {
								duplicates++;
								continue;
							}

							// Save new article
							NewsArticleEntity article = convertToEntity(apiArticle, locality, category);
							newsArticleRepository.save(article);
							imported++;
						} catch (Exception articleException) {
							logger.error("Error saving article {}: {}", apiArticle.getUri(),
									articleException.getMessage());
							// Continue with next article
						}
					}
				}

				// Small delay to respect rate limits
				Thread.sleep(200);

			} catch (Exception e) {
				logger.error("Error importing news for keyword {}: {}", keyword, e.getMessage());
			}
		}

		return new ImportResult(imported, duplicates);
	}

	private NewsArticleEntity convertToEntity(NewsApiResponse.NewsApiArticle apiArticle, Locality locality,
			IssueTypeModel category) {

		// Parse published date
		LocalDateTime publishedAt = parseDateTime(apiArticle.getDateTime(), apiArticle.getDate(), apiArticle.getTime());

		// Get author name
		String authorName = null;
		if (apiArticle.getAuthors() != null && !apiArticle.getAuthors().isEmpty()) {
			authorName = apiArticle.getAuthors().get(0).getName();
		}

		return NewsArticleEntity.builder().externalId(apiArticle.getUri()).title(apiArticle.getTitle())
				.description(extractDescription(apiArticle.getBody())).content(apiArticle.getBody()).category(category)
				.sourceId(apiArticle.getSource() != null ? apiArticle.getSource().getUri() : null)
				.sourceName(apiArticle.getSource() != null ? apiArticle.getSource().getTitle() : "Unknown")
				.author(authorName).url(apiArticle.getUrl()).urlToImage(apiArticle.getImageUrl())
				.publishedAt(publishedAt).locality(locality).build();
	}

	private LocalDateTime parseDateTime(String dateTime, String date, String time) {
		try {
			if (dateTime != null && !dateTime.isEmpty()) {
				return LocalDateTime.parse(dateTime, DateTimeFormatter.ISO_DATE_TIME);
			} else if (date != null && !date.isEmpty()) {
				LocalDate localDate = LocalDate.parse(date);
				return localDate.atStartOfDay();
			}
		} catch (Exception e) {
			logger.warn("Error parsing date: {}", e.getMessage());
		}
		return LocalDateTime.now();
	}

	private String extractDescription(String body) {
		if (body == null || body.isEmpty()) {
			return "";
		}
		// Extract first 2-3 sentences (approx 300 chars)
		String[] sentences = body.split("\\. ");
		StringBuilder description = new StringBuilder();
		int count = 0;
		for (String sentence : sentences) {
			if (count >= 2 || description.length() > 300) {
				break;
			}
			description.append(sentence).append(". ");
			count++;
		}
		return description.toString().trim();
	}

	private String[] getCategoryKeywords(IssueTypeModel category) {
		return switch (category) {
			case POTHOLE -> POTHOLE_KEYWORDS;
			case WASTE -> WASTE_KEYWORDS;
			case FOOTPATH -> FOOTPATH_KEYWORDS;
			case POLLUTION -> POLLUTION_KEYWORDS;
			case HYGIENE -> HYGIENE_KEYWORDS;
			case SAFETY -> SAFETY_KEYWORDS;
			default -> new String[0];
		};
	}

	private static class ImportResult {
		int imported;
		int duplicates;

		ImportResult(int imported, int duplicates) {
			this.imported = imported;
			this.duplicates = duplicates;
		}
	}
}
