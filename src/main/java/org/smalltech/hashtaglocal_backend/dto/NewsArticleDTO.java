package org.smalltech.hashtaglocal_backend.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NewsArticleDTO {

	private String id;
	private String title;
	private String description;
	private String content;
	private String category;
	private SourceDTO source;
	private String author;
	private String url;
	private String urlToImage;

	@JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss'Z'")
	private LocalDateTime publishedAt;

	private String location;

	@Data
	@Builder
	@NoArgsConstructor
	@AllArgsConstructor
	public static class SourceDTO {
		private String id;
		private String name;
	}
}
