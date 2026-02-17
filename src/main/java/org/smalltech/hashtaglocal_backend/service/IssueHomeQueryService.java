package org.smalltech.hashtaglocal_backend.service;

import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.smalltech.hashtaglocal_backend.model.IssueStatusModel;
import org.smalltech.hashtaglocal_backend.repository.IssueRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class IssueHomeQueryService {

	private final IssueRepository issueRepository;

	public List<org.smalltech.hashtaglocal_backend.entity.IssueEntity> findRecentIssues(String localityHashtag) {
		LocalDateTime since = LocalDateTime.now().minusMonths(6);

		List<IssueStatusModel> statuses = List.of(IssueStatusModel.OPEN, IssueStatusModel.ONHOLD,
				IssueStatusModel.PENDING, IssueStatusModel.RESOLVED);

		if (localityHashtag != null && !localityHashtag.isBlank()) {
			return issueRepository.findByStatusInAndCreatedAtAfterAndLocalityHashtagOrderByCreatedAtDesc(statuses,
					since, localityHashtag);
		}

		return issueRepository.findByStatusInAndCreatedAtAfterOrderByCreatedAtDesc(statuses, since);
	}
}
