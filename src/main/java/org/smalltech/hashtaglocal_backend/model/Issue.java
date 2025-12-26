package org.smalltech.hashtaglocal_backend.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class Issue {
	private User user;
	private Location location;
	private String type;
	private String description;

	@JsonProperty("created_at")
	private String createdAt;

	@JsonProperty("media_urls")
	private List<Media> mediaUrls;

	@JsonProperty("vote_count")
	private int voteCount;

	@JsonProperty("verify_count")
	private int verifyCount;
	private String status;
	private int rank;
}
