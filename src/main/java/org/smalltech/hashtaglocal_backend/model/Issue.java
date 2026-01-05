package org.smalltech.hashtaglocal_backend.model;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Issue {
	private User user;
	private Location location;
	private String type;
	private String description;
	private String createdAt;
	private List<Media> mediaUrls;
	private int voteCount;
	private int verifyCount;
	private String status;
	private int rank;
	private ViewerContext viewerContext;
}
