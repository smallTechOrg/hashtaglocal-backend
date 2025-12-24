package org.smalltech.hashtaglocal_backend.model;

public class MediaLocation {

	private String lat;
	private String lng;

	public MediaLocation() {
	}

	public MediaLocation(String lat, String lng) {
		this.lat = lat;
		this.lng = lng;
	}

	public String getLat() {
		return lat;
	}

	public void setLat(String lat) {
		this.lat = lat;
	}

	public String getLng() {
		return lng;
	}

	public void setLng(String lng) {
		this.lng = lng;
	}
}
