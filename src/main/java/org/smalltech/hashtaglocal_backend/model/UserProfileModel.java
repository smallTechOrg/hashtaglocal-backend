package org.smalltech.hashtaglocal_backend.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserProfileModel {

	private String username;
	private String picture;
	private String hashtag;
}
