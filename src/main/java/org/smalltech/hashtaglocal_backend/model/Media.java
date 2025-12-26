package org.smalltech.hashtaglocal_backend.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class Media {
	private Location location;
	private String type;
	private String url;
}
