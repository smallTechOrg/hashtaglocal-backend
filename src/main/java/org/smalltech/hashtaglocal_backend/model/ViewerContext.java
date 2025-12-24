package org.smalltech.hashtaglocal_backend.model;

public class ViewerContext {
	private boolean upvote;

	public ViewerContext() {
	}

	public ViewerContext(boolean upvote) {
		this.upvote = upvote;
	}

	public boolean isUpvote() {
		return upvote;
	}

	public void setUpvote(boolean upvote) {
		this.upvote = upvote;
	}
}
