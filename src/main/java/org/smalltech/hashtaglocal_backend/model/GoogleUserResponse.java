package org.smalltech.hashtaglocal_backend.model;

import lombok.*;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class GoogleUserResponse {
	private String id;
	private String email;
	private String name;
	private String picture;
}
