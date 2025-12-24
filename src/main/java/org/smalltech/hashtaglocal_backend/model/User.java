package org.smalltech.hashtaglocal_backend.model;

public class User {
	private String username;
	private String profileUrl;

	public User() {
	}

	public User(String username, String profileUrl) {
		this.username = username;
		this.profileUrl = profileUrl;
	}

	public String getUsername() {
		return username;
	}

	public void setUsername(String username) {
		this.username = username;
	}

	public String getProfileUrl() {
		return profileUrl;
	}

	public void setProfileUrl(String profileUrl) {
		this.profileUrl = profileUrl;
	}
}
