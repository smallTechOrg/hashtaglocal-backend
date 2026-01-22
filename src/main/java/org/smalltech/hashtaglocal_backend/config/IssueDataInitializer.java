package org.smalltech.hashtaglocal_backend.config;

import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import org.smalltech.hashtaglocal_backend.entity.IssueEntity;
import org.smalltech.hashtaglocal_backend.entity.UserEntity;
import org.smalltech.hashtaglocal_backend.model.IssueStatusModel;
import org.smalltech.hashtaglocal_backend.model.IssueTypeModel;
import org.smalltech.hashtaglocal_backend.repository.IssueRepository;
import org.smalltech.hashtaglocal_backend.repository.UserRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Configuration;

@Configuration
@RequiredArgsConstructor
public class IssueDataInitializer implements CommandLineRunner {

	private final IssueRepository issueRepository;
	private final UserRepository userRepository;

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
		if (userRepository.count() == 0) {

			UserEntity user;
			user = UserEntity.builder().username("Admin").locale("en").build();

			userRepository.save(user);
		}
	}
}
