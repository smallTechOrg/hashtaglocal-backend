package org.smalltech.hashtaglocal_backend.model;

import java.util.List;

public class Locality {
	private List<String> hashtags;

	public Locality() {
	}

	public Locality(List<String> hashtags) {
		this.hashtags = hashtags;
	}

	public List<String> getHashtags() {
		return hashtags;
	}

	public void setHashtags(List<String> hashtags) {
		this.hashtags = hashtags;
	}
}
