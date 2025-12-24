package org.smalltech.hashtaglocal_backend.model;

import java.time.LocalDateTime;
import java.util.List;

public class Issue {
	private User user;
	private Location location;
	private String type;
	private String description;
	private LocalDateTime createdAt;
	private List<Media> mediaUrls;
	private int voteCount;
	private int verifyCount;
	private String status;
	private int rank;

	public Issue() {
	}

	public Issue(User user, Location location, String type, String description, LocalDateTime createdAt,
			List<Media> mediaUrls, int voteCount, int verifyCount, String status, int rank) {
		this.user = user;
		this.location = location;
		this.type = type;
		this.description = description;
		this.createdAt = createdAt;
		this.mediaUrls = mediaUrls;
		this.voteCount = voteCount;
		this.verifyCount = verifyCount;
		this.status = status;
		this.rank = rank;
	}

	public User getUser() {
		return user;
	}

	public void setUser(User user) {
		this.user = user;
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

	public String getDescription() {
		return description;
	}

	public void setDescription(String description) {
		this.description = description;
	}

	public LocalDateTime getCreatedAt() {
		return createdAt;
	}

	public void setCreatedAt(LocalDateTime createdAt) {
		this.createdAt = createdAt;
	}

	public List<Media> getMediaUrls() {
		return mediaUrls;
	}

	public void setMediaUrls(List<Media> mediaUrls) {
		this.mediaUrls = mediaUrls;
	}

	public int getVoteCount() {
		return voteCount;
	}

	public void setVoteCount(int voteCount) {
		this.voteCount = voteCount;
	}

	public int getVerifyCount() {
		return verifyCount;
	}

	public void setVerifyCount(int verifyCount) {
		this.verifyCount = verifyCount;
	}

	public String getStatus() {
		return status;
	}

	public void setStatus(String status) {
		this.status = status;
	}

	public int getRank() {
		return rank;
	}

	public void setRank(int rank) {
		this.rank = rank;
	}
}
