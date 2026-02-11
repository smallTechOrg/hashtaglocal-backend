package org.smalltech.hashtaglocal_backend.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.smalltech.hashtaglocal_backend.dto.newsapi.NewsApiRequest;
import org.smalltech.hashtaglocal_backend.dto.newsapi.NewsApiResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

/**
 * Service to interact with NewsAPI.ai (EventRegistry) Documentation:
 * https://newsapi.ai/documentation
 */
@Service
public class NewsApiService {

	private static final Logger logger = LoggerFactory.getLogger(NewsApiService.class);
	private static final String BASE_URL = "https://eventregistry.org/api/v1/article/getArticles";

	private final RestClient restClient;
	private final ObjectMapper objectMapper;

	@Value("${newsapi.api-key}")
	private String apiKey;

	public NewsApiService(RestClient.Builder restClientBuilder, ObjectMapper objectMapper) {
		this.restClient = restClientBuilder.build();
		this.objectMapper = objectMapper;
	}

	/**
	 * Search for articles using NewsAPI.ai
	 */
	public NewsApiResponse searchArticles(NewsApiRequest request) {
		try {
			// Build the complex query structure required by EventRegistry
			Map<String, Object> queryBody = buildQueryBody(request);

			logger.info("Fetching articles from NewsAPI with query: {}", request.getQuery());

			// Make POST request
			String response = restClient.post().uri(buildUri()).header("Content-Type", "application/json")
					.body(queryBody).retrieve().body(String.class);

			// Parse response
			return parseResponse(response);

		} catch (Exception e) {
			logger.error("Error fetching articles from NewsAPI: {}", e.getMessage(), e);
			throw new RuntimeException("Failed to fetch articles from NewsAPI", e);
		}
	}

	private String buildUri() {
		return BASE_URL;
	}

	private Map<String, Object> buildQueryBody(NewsApiRequest request) {
		Map<String, Object> body = new HashMap<>();

		// Add API key to the body
		body.put("apiKey", apiKey);

		// Query section
		Map<String, Object> query = new HashMap<>();
		query.put("$query", buildQuery(request));
		body.put("query", query);

		// Result type
		body.put("resultType", "articles");

		// Articles configuration
		Map<String, Object> articlesSortBy = new HashMap<>();
		articlesSortBy.put(request.getSortBy() != null ? request.getSortBy() : "date",
				request.getSortByAsc() != null ? request.getSortByAsc() : "desc");

		Map<String, Object> articlesConfig = new HashMap<>();
		articlesConfig.put("page", request.getArticlesPage() != null ? request.getArticlesPage() : 1);
		articlesConfig.put("count", request.getArticlesCount() != null ? request.getArticlesCount() : 100);
		articlesConfig.put("sortBy", articlesSortBy);
		articlesConfig.put("includeArticleImage", true);
		articlesConfig.put("includeArticleBody", true);

		body.put("articlesSortBy", articlesSortBy);
		body.put("articlesPage", articlesConfig.get("page"));
		body.put("articlesCount", articlesConfig.get("count"));
		body.put("articlesIncludeArticleImage", true);
		body.put("articlesIncludeArticleBody", true);

		return body;
	}

	private Map<String, Object> buildQuery(NewsApiRequest request) {
		Map<String, Object> queryMap = new HashMap<>();

		// Add keywords if query is provided
		if (request.getQuery() != null && !request.getQuery().isEmpty()) {
			queryMap.put("$and", List.of(Map.of("keyword", request.getQuery(), "keywordLoc", "body")));
		}

		// Add location filter if provided
		if (request.getLocationUri() != null && !request.getLocationUri().isEmpty()) {
			List<Map<String, Object>> andConditions = new ArrayList<>();
			if (queryMap.containsKey("$and")) {
				andConditions.addAll((List<Map<String, Object>>) queryMap.get("$and"));
			}
			andConditions.add(Map.of("locationUri", request.getLocationUri()));
			queryMap.put("$and", andConditions);
		}

		// Add date range if provided
		if (request.getDateStart() != null || request.getDateEnd() != null) {
			Map<String, String> dateRange = new HashMap<>();
			if (request.getDateStart() != null) {
				dateRange.put("$gte", request.getDateStart());
			}
			if (request.getDateEnd() != null) {
				dateRange.put("$lte", request.getDateEnd());
			}

			List<Map<String, Object>> andConditions = new ArrayList<>();
			if (queryMap.containsKey("$and")) {
				andConditions.addAll((List<Map<String, Object>>) queryMap.get("$and"));
			}
			andConditions.add(Map.of("dateStart", dateRange.get("$gte"), "dateEnd", dateRange.get("$lte")));
			queryMap.put("$and", andConditions);
		}

		// Add language filter
		if (request.getLang() != null) {
			List<Map<String, Object>> andConditions = new ArrayList<>();
			if (queryMap.containsKey("$and")) {
				andConditions.addAll((List<Map<String, Object>>) queryMap.get("$and"));
			}
			andConditions.add(Map.of("lang", request.getLang()));
			queryMap.put("$and", andConditions);
		}

		return queryMap;
	}

	private NewsApiResponse parseResponse(String response) {
		try {
			JsonNode root = objectMapper.readTree(response);
			NewsApiResponse apiResponse = new NewsApiResponse();

			// Parse articles section
			if (root.has("articles")) {
				JsonNode articlesNode = root.get("articles");
				NewsApiResponse.ArticlesResult articlesResult = new NewsApiResponse.ArticlesResult();

				articlesResult.setCount(articlesNode.has("count") ? articlesNode.get("count").asInt() : 0);
				articlesResult.setPages(articlesNode.has("pages") ? articlesNode.get("pages").asInt() : 0);
				articlesResult.setPage(articlesNode.has("page") ? articlesNode.get("page").asInt() : 1);

				// Parse articles results
				if (articlesNode.has("results")) {
					List<NewsApiResponse.NewsApiArticle> articles = new ArrayList<>();
					for (JsonNode articleNode : articlesNode.get("results")) {
						NewsApiResponse.NewsApiArticle article = parseArticle(articleNode);
						if (article != null) {
							articles.add(article);
						}
					}
					articlesResult.setResults(articles);
				}

				apiResponse.setArticles(articlesResult);
			}

			return apiResponse;
		} catch (Exception e) {
			logger.error("Error parsing NewsAPI response: {}", e.getMessage(), e);
			throw new RuntimeException("Failed to parse NewsAPI response", e);
		}
	}

	private NewsApiResponse.NewsApiArticle parseArticle(JsonNode articleNode) {
		try {
			NewsApiResponse.NewsApiArticle article = new NewsApiResponse.NewsApiArticle();

			article.setUri(articleNode.has("uri") ? articleNode.get("uri").asText() : null);
			article.setTitle(articleNode.has("title") ? articleNode.get("title").asText() : null);
			article.setBody(articleNode.has("body") ? articleNode.get("body").asText() : null);
			article.setUrl(articleNode.has("url") ? articleNode.get("url").asText() : null);
			article.setImageUrl(articleNode.has("image") ? articleNode.get("image").asText() : null);
			article.setDate(articleNode.has("date") ? articleNode.get("date").asText() : null);
			article.setTime(articleNode.has("time") ? articleNode.get("time").asText() : null);
			article.setDateTime(articleNode.has("dateTime") ? articleNode.get("dateTime").asText() : null);

			// Parse source
			if (articleNode.has("source")) {
				JsonNode sourceNode = articleNode.get("source");
				NewsApiResponse.NewsApiArticle.NewsApiSource source = new NewsApiResponse.NewsApiArticle.NewsApiSource();
				source.setUri(sourceNode.has("uri") ? sourceNode.get("uri").asText() : null);
				source.setTitle(sourceNode.has("title") ? sourceNode.get("title").asText() : null);
				article.setSource(source);
			}

			// Parse authors
			if (articleNode.has("authors")) {
				List<NewsApiResponse.NewsApiArticle.NewsApiAuthor> authors = new ArrayList<>();
				for (JsonNode authorNode : articleNode.get("authors")) {
					NewsApiResponse.NewsApiArticle.NewsApiAuthor author = new NewsApiResponse.NewsApiArticle.NewsApiAuthor();
					author.setName(authorNode.has("name") ? authorNode.get("name").asText() : null);
					author.setUri(authorNode.has("uri") ? authorNode.get("uri").asText() : null);
					authors.add(author);
				}
				article.setAuthors(authors);
			}

			return article;
		} catch (Exception e) {
			logger.error("Error parsing article: {}", e.getMessage());
			return null;
		}
	}

	/**
	 * Helper method to format date for NewsAPI
	 */
	public static String formatDate(LocalDate date) {
		return date.format(DateTimeFormatter.ISO_DATE);
	}
}
