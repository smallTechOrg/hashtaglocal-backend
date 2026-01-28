package org.smalltech.hashtaglocal_backend.model;

public class GoogleUserResponse {

	private String id;
	private String email;
	private String name;
	private String picture;

	public GoogleUserResponse() {
	}

	public GoogleUserResponse(String id, String email, String name, String picture) {
		this.id = id;
		this.email = email;
		this.name = name;
		this.picture = picture;
	}

	public String getId() {
		return id;
	}

	public String getEmail() {
		return email;
	}

	public String getName() {
		return name;
	}

	public String getPicture() {
		return picture;
	}
}
