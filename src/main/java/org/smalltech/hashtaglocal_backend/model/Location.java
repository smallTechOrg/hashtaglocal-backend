package org.smalltech.hashtaglocal_backend.model;

public class Location {
	private String lat;
	private String lng;
	private Locality locality;
	private String address;
	private String colloquialName;

	public Location() {
	}

	public Location(String lat, String lng, Locality locality, String address, String colloquialName) {
		this.lat = lat;
		this.lng = lng;
		this.locality = locality;
		this.address = address;
		this.colloquialName = colloquialName;
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

	public Locality getLocality() {
		return locality;
	}

	public void setLocality(Locality locality) {
		this.locality = locality;
	}

	public String getAddress() {
		return address;
	}

	public void setAddress(String address) {
		this.address = address;
	}

	public String getColloquialName() {
		return colloquialName;
	}

	public void setColloquialName(String colloquialName) {
		this.colloquialName = colloquialName;
	}
}
