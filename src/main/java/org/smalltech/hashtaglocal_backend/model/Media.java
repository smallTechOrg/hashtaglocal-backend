package org.smalltech.hashtaglocal_backend.model;

public class Media {
	private Location location;
	private String type;
	private String url;

	public Media() {
	}

	public Media(Location location, String type, String url) {
		this.location = location;
		this.type = type;
		this.url = url;
	}

	public Location getLocation() {
		return location;
	}

	public void setLocation(Location location) {
		this.location = location;
	}

	public String getType() {
		return type;
	}

	public void setType(String type) {
		this.type = type;
	}

	public String getUrl() {
		return url;
	}

	public void setUrl(String url) {
		this.url = url;
	}
}
