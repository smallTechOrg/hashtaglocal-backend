package org.smalltech.hashtaglocal_backend.dto;

import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NewsResponse {

	private String status;
	private long totalResults;
	private int page;
	private int pageSize;
	private List<NewsArticleDTO> articles;
}
