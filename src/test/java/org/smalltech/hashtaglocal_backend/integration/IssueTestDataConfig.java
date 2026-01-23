package org.smalltech.hashtaglocal_backend.integration;

import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import org.smalltech.hashtaglocal_backend.entity.IssueEntity;
import org.smalltech.hashtaglocal_backend.model.IssueStatusModel;
import org.smalltech.hashtaglocal_backend.model.IssueTypeModel;
import org.smalltech.hashtaglocal_backend.repository.IssueRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.test.context.TestConfiguration;

@TestConfiguration
@RequiredArgsConstructor
public class IssueTestDataConfig implements CommandLineRunner {

	private final IssueRepository issueRepository;

	@Override
	public void run(String... args) {
		// Only insert if table is empty
		if (issueRepository.count() == 0) {
			IssueEntity issue;
			issue = IssueEntity.builder().key("JPR-001").type(IssueTypeModel.POTHOLE).status(IssueStatusModel.OPEN)
					.description("Large pothole causing traffic issues")
					.createdAt(LocalDateTime.parse("2025-12-26T18:00:00"))
					.updatedAt(LocalDateTime.parse("2025-12-26T18:00:00")).build();

			issueRepository.save(issue);
		}
	}
}
