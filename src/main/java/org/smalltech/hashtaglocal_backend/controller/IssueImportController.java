package org.smalltech.hashtaglocal_backend.controller;

import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.smalltech.hashtaglocal_backend.entity.IssueImportJob;
import org.smalltech.hashtaglocal_backend.entity.IssueImportStatus;
import org.smalltech.hashtaglocal_backend.model.IssueImportSource;
import org.smalltech.hashtaglocal_backend.repository.IssueImportJobRepository;
import org.smalltech.hashtaglocal_backend.repository.IssueImportStatusRepository;
import org.smalltech.hashtaglocal_backend.service.import_job.IssueImportService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/admin/issues")
@RequiredArgsConstructor
@Slf4j
public class IssueImportController {

	private final IssueImportService issueImportService;
	private final IssueImportJobRepository jobRepository;
	private final IssueImportStatusRepository statusRepository;

	@PostMapping("/import")
	public IssueImportJob importIssues(@RequestParam(defaultValue = "BLR_PAGES") IssueImportSource source) {
		log.info("Starting issue import for source {}", source);
		return switch (source) {
			case BLR_PAGES -> issueImportService.importBlrPages();
		};
	}

	@GetMapping("/import/{jobId}")
	public IssueImportJob getJob(@PathVariable Long jobId) {
		return jobRepository.findById(jobId).orElseThrow(() -> new RuntimeException("Job not found: " + jobId));
	}

	@GetMapping("/import/{jobId}/status")
	public List<IssueImportStatus> getStatuses(@PathVariable Long jobId) {
		return statusRepository.findByJobId(jobId);
	}
}
