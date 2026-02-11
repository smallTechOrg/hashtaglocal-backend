package org.smalltech.hashtaglocal_backend.dto.newsapi;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import lombok.Data;

/**
 * NewsAPI.ai API Response DTOs Based on EventRegistry API structure
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class NewsApiResponse {

	private ArticlesResult articles;

	@Data
	@JsonIgnoreProperties(ignoreUnknown = true)
	public static class ArticlesResult {

		private int count;

		private int pages;

		private int page;

		private List<NewsApiArticle> results;
	}

	@Data
	@JsonIgnoreProperties(ignoreUnknown = true)
	public static class NewsApiArticle {

		private String uri;

		private String title;

		private String body;

		private String url;

		@JsonProperty("image")
		private String imageUrl;

		private String date;

		private String time;

		private String dateTime;

		private NewsApiSource source;

		private List<NewsApiAuthor> authors;

		@Data
		@JsonIgnoreProperties(ignoreUnknown = true)
		public static class NewsApiSource {

			private String uri;

			private String title;
		}

		@Data
		@JsonIgnoreProperties(ignoreUnknown = true)
		public static class NewsApiAuthor {

			private String name;

			private String uri;
		}
	}
}
