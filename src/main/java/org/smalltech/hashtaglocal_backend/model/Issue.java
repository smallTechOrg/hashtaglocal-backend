package org.smalltech.hashtaglocal_backend.model;

import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

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
}
