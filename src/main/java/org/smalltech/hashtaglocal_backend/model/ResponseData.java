package org.smalltech.hashtaglocal_backend.model;

public class ResponseData {
	private Issue issue;
	private ViewerContext viewerContext;

	public ResponseData() {
	}

	public ResponseData(Issue issue, ViewerContext viewerContext) {
		this.issue = issue;
		this.viewerContext = viewerContext;
	}

	public Issue getIssue() {
		return issue;
	}

	public void setIssue(Issue issue) {
		this.issue = issue;
	}

	public ViewerContext getViewerContext() {
		return viewerContext;
	}

	public void setViewerContext(ViewerContext viewerContext) {
		this.viewerContext = viewerContext;
	}
}
