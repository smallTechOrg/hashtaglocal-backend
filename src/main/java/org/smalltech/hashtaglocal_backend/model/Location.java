package org.smalltech.hashtaglocal_backend.model;

import lombok.*;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Location {
	private String lat;
	private String lng;
	private Locality locality;
	private String address;
	private String colloquialName;
}
