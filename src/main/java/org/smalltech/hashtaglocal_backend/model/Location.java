package org.smalltech.hashtaglocal_backend.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor

public class Location {
	private String lat;
	private String lng;
	private Locality locality;
	private String address;
	private String colloquialName;
}
