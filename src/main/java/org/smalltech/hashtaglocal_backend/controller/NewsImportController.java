package org.smalltech.hashtaglocal_backend.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.smalltech.hashtaglocal_backend.entity.NewsImportJob;
import org.smalltech.hashtaglocal_backend.service.NewsImportService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * Controller for news import operations
 */
@RestController
@RequestMapping("/api/admin/news")
@Tag(name = "News Import", description = "News import administration API")
public class NewsImportController {

	private final NewsImportService newsImportService;

	public NewsImportController(NewsImportService newsImportService) {
		this.newsImportService = newsImportService;
	}

	@PostMapping("/import/{hashtag}")
	@Operation(summary = "Import news for a locality", description = "Fetches and stores news articles from NewsAPI.ai for the specified locality")
	public ResponseEntity<ImportResponse> importNews(@PathVariable String hashtag) {
		NewsImportJob job = newsImportService.importNewsForLocality(hashtag);

		ImportResponse response = new ImportResponse();
		response.setSuccess(job.getStatus() == NewsImportJob.ImportStatus.SUCCESS);
		response.setMessage(String.format("Import completed. Articles imported: %d, Duplicates skipped: %d",
				job.getArticlesImported(), job.getArticlesDuplicate()));
		response.setJobId(job.getId());
		response.setArticlesImported(job.getArticlesImported());
		response.setArticlesDuplicate(job.getArticlesDuplicate());
		response.setStatus(job.getStatus().toString());

		return ResponseEntity.ok(response);
	}

	public static class ImportResponse {
		private boolean success;
		private String message;
		private Long jobId;
		private Integer articlesImported;
		private Integer articlesDuplicate;
		private String status;

		// Getters and setters
		public boolean isSuccess() {
			return success;
		}

		public void setSuccess(boolean success) {
			this.success = success;
		}

		public String getMessage() {
			return message;
		}

		public void setMessage(String message) {
			this.message = message;
		}

		public Long getJobId() {
			return jobId;
		}

		public void setJobId(Long jobId) {
			this.jobId = jobId;
		}

		public Integer getArticlesImported() {
			return articlesImported;
		}

		public void setArticlesImported(Integer articlesImported) {
			this.articlesImported = articlesImported;
		}

		public Integer getArticlesDuplicate() {
			return articlesDuplicate;
		}

		public void setArticlesDuplicate(Integer articlesDuplicate) {
			this.articlesDuplicate = articlesDuplicate;
		}

		public String getStatus() {
			return status;
		}

		public void setStatus(String status) {
			this.status = status;
		}
	}
}
