package org.smalltech.hashtaglocal_backend.dto.newsapi;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NewsApiRequest {

	private String query;

	private String locationUri;

	private Integer articlesPage;

	private Integer articlesCount;

	private String dateStart;

	private String dateEnd;

	private String lang;

	private String sortBy;

	private String sortByAsc;
}
