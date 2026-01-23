package org.smalltech.hashtaglocal_backend.model;

import lombok.*;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Media {
	private Location location;
	private String type;
	private String url;
}
