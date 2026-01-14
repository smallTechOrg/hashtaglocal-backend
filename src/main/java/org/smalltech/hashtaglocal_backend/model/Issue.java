package org.smalltech.hashtaglocal_backend.model;

import java.util.List;
import lombok.*;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Issue {
	private Long id;
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
