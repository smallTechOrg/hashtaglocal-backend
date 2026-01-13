package org.smalltech.hashtaglocal_backend.config;

import lombok.RequiredArgsConstructor;
import org.smalltech.hashtaglocal_backend.entity.IssueEntity;
import org.smalltech.hashtaglocal_backend.repository.IssueRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Configuration;

@Configuration
@RequiredArgsConstructor
public class IssueDataInitializer implements CommandLineRunner {

	private final IssueRepository issueRepository;

	@Override
	public void run(String... args) {
		// Only insert if table is empty
		if (issueRepository.count() == 0) {

			IssueEntity issue = IssueEntity.builder().key("JPR-001").type("pothole").status("OPEN")
					.description("Large pothole causing traffic issues").createdAt("2025-12-26T18:00:00")
					.updatedAt("2025-12-26T18:00:00").build();

			issueRepository.save(issue);
		}
	}
}
